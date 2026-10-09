# Backend (mhd-api)

Small Node.js service that answers the phone app: closest stops, departures with
real-time delays, and nearby vehicles. Design: [docs/SPEC.md](../docs/SPEC.md), section 5.

- Timetable: KORDIS GTFS (`gtfs.zip`), converted daily into a read-only SQLite file
  `data/timetable.db` (~55 MB) by `npm run build-db`. The server never parses GTFS.
  The build also reads data.Brno's weekly copy of the feed to map older trip_ids (which the
  real-time feed may still use after KORDIS renumbers trips) onto the current ones; pass
  `--alias-source none` to skip that.
- Real-time: KORDIS GTFS-RT (`gtfsReal.dat`), fetched on demand and cached for 15 s.
  The feed has vehicle positions only; delays are derived in `src/realtime/tracker.ts`.

## Local development

```sh
npm install
npm run build
npm run build-db          # downloads gtfs.zip; or: node dist/build/buildDb.js --source path/to/gtfs.zip
npm start                 # http://localhost:8080
npm test                  # unit + API tests on a small fixture feed
```

Environment variables:

| Variable | Default | Meaning |
|----------|---------|---------|
| `PORT` | `8080` | HTTP port (Cloud Run sets it) |
| `API_KEY` | unset | When set, every request except `/v1/health` needs header `x-api-key` |
| `TIMETABLE_DB` | `data/timetable.db` | Path to the database |
| `REALTIME_URL` | KORDIS `gtfsReal.dat` | Real-time feed URL |

Behind an HTTP proxy, Node's built-in `fetch` only uses `HTTPS_PROXY` when started with
`NODE_USE_ENV_PROXY=1` (Node 22.21+). Not needed on Cloud Run.

## API

All times are epoch seconds, distances in metres. Mode codes: `T` tram, `B` bus (incl.
trolleybus), `V` train, `L` boat.

| Endpoint | Returns |
|----------|---------|
| `GET /v1/home?lat=&lon=&n=4` | Closest platform and its next `n` departures (one round trip for the watch home screen) |
| `GET /v1/departures?platform=U1073Z2&n=4[&lat=&lon=]` | Departures from a platform (used by the reverse button and the stop list) |
| `GET /v1/nearby?lat=&lon=&limit=12` | Stops ordered by distance, each with its platforms |
| `GET /v1/board?platforms=U1073Z1,U1201Z2&n=8` | A stop list: the next `n` departures of up to 8 platforms in one timeline, each with its platform (`p`) and stop name (`sn`) |
| `GET /v1/vehicles?lat=&lon=&r=800` | Up to 15 live vehicles and 3 stops, as metres east (`dx`) / north (`dy`) of the user; `b` heading in degrees (-1 unknown), `h` headsign |
| `GET /v1/health` | Timetable validity and real-time feed age (no API key needed) |

Example `/v1/home` response (shortened):

```json
{
  "t": 1791485744, "g": "U1073N2860", "stop": "Česká", "p": "U1073Z1",
  "dir": "Náměstí Míru, Starý Lískovec, smyčka", "opp": "U1073Z2", "d": 20,
  "pl": [{ "id": "U1073Z1", "dir": "Náměstí Míru, Starý Lískovec, smyčka", "l": "3 4 5 6 H4", "la": 49.19811, "lo": 16.60607 }],
  "dep": [{ "l": "5", "m": "T", "h": "Ústřední hřbitov - smyčka", "e": 1791485760, "s": 1791485760, "dl": 0, "lv": 1 }]
}
```

`e` expected departure, `s` scheduled departure, `dl` delay in seconds, `lv` 1 when a live
vehicle position was used, `opp` the platform for the reverse button, `pl` all platforms of the stop (with coordinates `la`/`lo`).

## Deployment (Google Cloud Run)

`.github/workflows/backend.yml` tests every push. On `main`, and daily at 05:20 UTC to pick up new
KORDIS exports, it builds `timetable.db`, builds the image, pushes it to Artifact
Registry and deploys to Cloud Run. The deploy job is skipped until the variables below exist.

One-time setup (Cloud Shell or a terminal with `gcloud`; replace `PROJECT_ID`):

```sh
PROJECT_ID=your-project-id
REGION=europe-west1                     # a Tier 1 region with the full free allowance
REPO=JuliaJamnicka/BrnoMHDClosestStop
gcloud config set project $PROJECT_ID
PROJECT_NUMBER=$(gcloud projects describe $PROJECT_ID --format='value(projectNumber)')

gcloud services enable run.googleapis.com artifactregistry.googleapis.com \
  secretmanager.googleapis.com iamcredentials.googleapis.com

# image repository, keeping only the 2 newest images
gcloud artifacts repositories create mhd --repository-format=docker --location=$REGION
cat > /tmp/cleanup.json <<'EOF'
[{"name":"keep-2","action":{"type":"Keep"},"mostRecentVersions":{"keepCount":2}},
 {"name":"delete-rest","action":{"type":"Delete"},"condition":{"tagState":"any"}}]
EOF
gcloud artifacts repositories set-cleanup-policies mhd --location=$REGION --policy=/tmp/cleanup.json

# API key used by the phone app
openssl rand -hex 24 | gcloud secrets create mhd-api-key --data-file=-

# service account used by GitHub Actions
gcloud iam service-accounts create github-deploy
SA=github-deploy@$PROJECT_ID.iam.gserviceaccount.com
for role in roles/run.admin roles/artifactregistry.writer roles/iam.serviceAccountUser; do
  gcloud projects add-iam-policy-binding $PROJECT_ID --member=serviceAccount:$SA --role=$role
done
# identity the running service uses; it may only read the API key secret
gcloud iam service-accounts create mhd-runtime
gcloud secrets add-iam-policy-binding mhd-api-key \
  --member=serviceAccount:mhd-runtime@$PROJECT_ID.iam.gserviceaccount.com \
  --role=roles/secretmanager.secretAccessor

# keyless login from GitHub Actions (Workload Identity Federation), limited to this repository
gcloud iam workload-identity-pools create github --location=global
gcloud iam workload-identity-pools providers create-oidc github --location=global \
  --workload-identity-pool=github --issuer-uri=https://token.actions.githubusercontent.com \
  --attribute-mapping=google.subject=assertion.sub,attribute.repository=assertion.repository \
  --attribute-condition="assertion.repository=='$REPO'"
gcloud iam service-accounts add-iam-policy-binding $SA --role=roles/iam.workloadIdentityUser \
  --member="principalSet://iam.googleapis.com/projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/github/attribute.repository/$REPO"

echo "GCP_WIF_PROVIDER=projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/github/providers/github"
echo "GCP_SERVICE_ACCOUNT=$SA"
```

Then add these as **repository variables** (GitHub: Settings > Secrets and variables >
Actions > Variables): `GCP_PROJECT_ID`, `GCP_REGION`, `GCP_WIF_PROVIDER`, `GCP_SERVICE_ACCOUNT`.
Also set a budget alert (Billing > Budgets & alerts, e.g. 1 EUR).

Read the API key for the phone app with:

```sh
gcloud secrets versions access latest --secret=mhd-api-key
```

## Data licence

Timetable and real-time data: KORDIS JMK / data.Brno, CC BY 4.0. The phone and watch apps
show this attribution.
