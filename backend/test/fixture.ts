// A tiny GTFS feed shaped like the IDS JMK one, for tests.
//
// Stop group "Česká" (U1N1) has two platforms: U1Z1 (towards Řečkovice) and U1Z2 (towards Komárov).
// Tram 4: U2Z1 -> U1Z1 -> U3Z1 (direction 0) and back U3Z2 -> U1Z2 -> U2Z2 (direction 1).
// Bus N93: a night trip at U1Z1 after midnight (24:10) on the previous service day.
// Bus N91: one trip from Kořískova (U4Z1, ~280 m north of Česká) at 9:40.

import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { buildDatabase } from '../src/build/buildDb.js';

const files: Record<string, string> = {
  'routes.txt': `route_id,agency_id,route_short_name,route_long_name,route_type
L4D99,99,4,"Řečkovice - Komárov",0
L93D99,99,N93,"Night",3
L91D99,99,N91,"Night",3
`,
  'stops.txt': `stop_id,stop_name,stop_lat,stop_lon,zone_id,location_type,parent_station,wheelchair_boarding,platform_code
"U1N1","Česká",49.1980,16.6060,"100",1,,0,
"U1Z1","Česká",49.1981,16.6061,"100",0,U1N1,1,1
"U1Z2","Česká",49.1979,16.6059,"100",0,U1N1,1,2
"U2N1","Konečného náměstí",49.2050,16.5950,"100",1,,0,
"U2Z1","Konečného náměstí",49.2050,16.5950,"100",0,U2N1,1,1
"U2Z2","Konečného náměstí",49.2051,16.5951,"100",0,U2N1,1,2
"U3N1","Řečkovice",49.2400,16.5800,"100",1,,0,
"U3Z1","Řečkovice",49.2400,16.5800,"100",0,U3N1,1,1
"U3Z2","Řečkovice",49.2401,16.5801,"100",0,U3N1,1,2
"U4N1","Kořískova",49.2006,16.6061,"100",1,,0,
"U4Z1","Kořískova",49.2006,16.6061,"100",0,U4N1,1,1
`,
  'trips.txt': `route_id,service_id,trip_id,trip_headsign,wheelchair_accessible,block_id,direction_id
L4D99,1,100,"Řečkovice",1,,0
L4D99,1,101,"Řečkovice",1,,0
L4D99,1,200,"Komárov",1,,1
L93D99,1,300,"Vranov, smyčka",1,,0
L91D99,1,400,"Řečkovice",1,,0
`,
  'stop_times.txt': `trip_id,arrival_time,departure_time,stop_id,stop_sequence,pickup_type,drop_off_type
100,10:00:00,10:00:00,U2Z1,1,0,0
100,10:04:00,10:05:00,U1Z1,2,0,0
100,10:15:00,10:15:00,U3Z1,3,0,1
101,10:20:00,10:20:00,U2Z1,1,0,0
101,10:25:00,10:25:00,U1Z1,2,0,0
101,10:35:00,10:35:00,U3Z1,3,0,1
200,10:00:00,10:00:00,U3Z2,1,0,0
200,10:10:00,10:10:00,U1Z2,2,0,0
200,10:20:00,10:20:00,U2Z2,3,0,1
300,24:05:00,24:05:00,U2Z1,1,0,0
300,24:10:00,24:10:00,U1Z1,2,0,0
300,24:30:00,24:30:00,U3Z1,3,0,1
400,09:40:00,09:40:00,U4Z1,1,0,0
400,09:50:00,09:50:00,U3Z1,2,0,1
`,
  'calendar.txt': `service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date
1,1,1,1,1,1,1,1,20260101,20261231
`,
  'calendar_dates.txt': `service_id,date,exception_type
1,20261225,2
`,
};

export async function buildFixtureDb(): Promise<string> {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'mhd-fixture-'));
  for (const [name, content] of Object.entries(files)) fs.writeFileSync(path.join(dir, name), content);
  const out = path.join(dir, 'timetable.db');
  await buildDatabase(dir, out, () => {});
  return out;
}
