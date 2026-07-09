import urllib.request
import urllib.parse
import json
import struct
import math
import os

BASE_URL = "https://data.grandlyon.com/geoserver/sytral/ows"
TYPES = [
    "sytral:tcl_sytral.tcllignemf_2_0_0",
    "sytral:tcl_sytral.tcllignetram_2_0_0",
    "sytral:tcl_sytral.tcllignebus_2_0_0",
    "sytral:tcl_sytral.tcllignefluv",
    "sytral:rx_rhonexpress.rxligne_2_0_0"
]

COORD_SCALE = 100000

def fetch_typename(typename):
    params = {
        "SERVICE": "WFS",
        "VERSION": "2.0.0",
        "request": "GetFeature",
        "outputFormat": "application/json",
        "SRSNAME": "EPSG:4326",
        "count": 10000,
        "typename": typename
    }
    url = f"{BASE_URL}?{urllib.parse.urlencode(params)}"
    print(f"Fetching {typename}...")
    req = urllib.request.Request(url)
    with urllib.request.urlopen(req) as response:
        return json.loads(response.read().decode('utf-8'))

def parse_multi_line_string(coords):
    if not coords or not isinstance(coords, list):
        return []
    if isinstance(coords[0], list) and isinstance(coords[0][0], (float, int)):
        return [coords]
    if isinstance(coords[0], list) and isinstance(coords[0][0], list):
        if isinstance(coords[0][0][0], (float, int)):
            return coords
        if isinstance(coords[0][0][0], list):
            res = []
            for c in coords:
                res.extend(parse_multi_line_string(c))
            return res
    return []

def main():
    features = []
    
    for t in TYPES:
        data = fetch_typename(t)
        raw_features = data.get("features", [])
        if not raw_features:
            raw_features = data.get("featureMember", []) or data.get("featureMembers", [])
            
        for rf in raw_features:
            geom = rf.get("geometry") or rf.get("the_geom") or rf.get("geom")
            if not geom or geom.get("type") not in ("LineString", "MultiLineString"):
                continue
            
            coords = geom.get("coordinates")
            paths = parse_multi_line_string(coords) if geom["type"] == "MultiLineString" else [coords]
            
            if not paths:
                continue
                
            orig_props = rf.get("properties", {})
            props = {}
            if "rx_" in t:
                gid = orig_props.get("gid") or orig_props.get("GID") or hash(rf.get("id", ""))
                props = {
                    "lineName": "RX",
                    "line_code": f"RX-{gid}",
                    "line_id": "RX",
                    "trace_type": "",
                    "trace_name": "Rhônexpress",
                    "direction": "ALLER",
                    "origin": "Gare Part-Dieu Villette",
                    "destination": "Aéroport St Exupéry -RX",
                    "origin_name": "Gare Part-Dieu Villette",
                    "destination_name": "Aéroport St Exupéry -RX",
                    "transport_type": "TRAM",
                    "start_date": "",
                    "end_date": None,
                    "line_type_code": "TRAM",
                    "line_type_name": "Tramway",
                    "sort_code": "",
                    "version_name": "",
                    "last_updated": "",
                    "last_updated_fme": "",
                    "gid": gid,
                    "color": "#E30613"
                }
            else:
                props = {
                    "lineName": orig_props.get("ligne", ""),
                    "line_code": orig_props.get("code_trace", ""),
                    "line_id": orig_props.get("code_ligne", ""),
                    "trace_type": orig_props.get("type_trace", ""),
                    "trace_name": orig_props.get("nom_trace", ""),
                    "direction": orig_props.get("sens"),
                    "origin": orig_props.get("origine", ""),
                    "destination": orig_props.get("destination", ""),
                    "origin_name": orig_props.get("nom_origine", ""),
                    "destination_name": orig_props.get("nom_destination", ""),
                    "transport_type": orig_props.get("famille_transport", ""),
                    "start_date": orig_props.get("date_debut", ""),
                    "end_date": orig_props.get("date_fin"),
                    "line_type_code": orig_props.get("code_type_ligne", ""),
                    "line_type_name": orig_props.get("nom_type_ligne", ""),
                    "sort_code": orig_props.get("code_tri_ligne", ""),
                    "version_name": orig_props.get("nom_version", ""),
                    "last_updated": orig_props.get("last_update", ""),
                    "last_updated_fme": orig_props.get("last_update_fme", ""),
                    "gid": orig_props.get("gid", 0),
                    "color": orig_props.get("couleur")
                }

            features.append({
                "id": rf.get("id", ""),
                "properties": props,
                "paths": paths
            })

    print(f"Total features collected: {len(features)}")
    
    # Write to bin
    out_dir = "app/src/commonMain/composeResources/files/lyon"
    os.makedirs(out_dir, exist_ok=True)
    out_path = os.path.join(out_dir, "lines.bin")
    
    with open(out_path, "wb") as f:
        # Magic
        f.write(b"LYL1")
        # schemaVersion u16
        f.write(struct.pack("<H", 1))
        # coordScale u32
        f.write(struct.pack("<I", COORD_SCALE))
        # lineCount u32
        f.write(struct.pack("<I", len(features)))
        
        for feat in features:
            props_json = json.dumps(feat["properties"]).encode('utf-8')
            f.write(struct.pack("<I", len(props_json)))
            f.write(props_json)
            
            paths = feat["paths"]
            f.write(struct.pack("<H", len(paths)))
            
            for path in paths:
                f.write(struct.pack("<I", len(path)))
                
                curr_lon = 0
                for pt in path:
                    scaled = int(pt[0] * COORD_SCALE)
                    delta = scaled - curr_lon
                    f.write(struct.pack("<i", delta))
                    curr_lon = scaled
                
                curr_lat = 0
                for pt in path:
                    scaled = int(pt[1] * COORD_SCALE)
                    delta = scaled - curr_lat
                    f.write(struct.pack("<i", delta))
                    curr_lat = scaled

    print(f"Generated {out_path} ({os.path.getsize(out_path)} bytes)")

if __name__ == "__main__":
    main()
