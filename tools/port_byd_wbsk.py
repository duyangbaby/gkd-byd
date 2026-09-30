"""Port the recovered, platform-independent WBSK routines without changing their transforms."""
import hashlib
import json
import re
import shutil
import sys
from pathlib import Path

source = Path(sys.argv[1])
repo = Path(__file__).resolve().parents[1]
target = repo / 'gkd-app/src/main/java/li/gkd/app/feature/vehicle/auto'
target.mkdir(parents=True, exist_ok=True)
names = {'lb3': 'WbskCodec', 'kb3': 'WbskTransform', 'mb3': 'WbskTables', 'c31': 'WbskRoundKey'}
hashes = {}
for old, new in list(names.items())[:3]:
    path = source / 'jadx_output/sources/a' / (old + '.java')
    text = path.read_text(encoding='utf-8')
    hashes[old + '.java'] = hashlib.sha256(path.read_bytes()).hexdigest()
    text = text.replace('package a;', 'package li.gkd.app.feature.vehicle.auto;')
    text = re.sub(r'/\*.*?\*/', '', text, flags=re.S)
    text = re.sub(r'^import (com\.byd|org\.eclipse|rikka\.).*?;\s*', '', text, flags=re.M)
    for constant, value in {'SignCheck.$stable': '8', 'MqttConnectOptions.MAX_INFLIGHT_DEFAULT': '10',
                            'ShizukuApiConstants.SERVER_VERSION': '13', 'Logger.WARNING': '2', 'Logger.FINEST': '7'}.items():
        text = text.replace(constant, value)
    text = text.replace('jc.o("WBSK table ", str, " size ")', 'new StringBuilder("WBSK table " + str + " size ")')
    # JADX emits a method return in a class initializer; smali exits the initialization loop.
    text = text.replace('if (i2 >= cArr.length) {\n                return;', 'if (i2 >= cArr.length) {\n                break;')
    for old_name, new_name in names.items():
        text = re.sub(r'\b' + old_name + r'\b', new_name, text)
    text = '// Ported from user-supplied 26.0928 DEX; transforms retained for protocol compatibility.\n' + text
    (target / (new + '.java')).write_text(text, encoding='utf-8')
(target / 'WbskRoundKey.java').write_text('package li.gkd.app.feature.vehicle.auto;\n\nfinal class WbskRoundKey {\n    int[] f132a;\n    int b;\n}\n', encoding='utf-8')
asset = source / 'apktool_resources/assets/byd/wbsk_tables.json'
asset_target = repo / 'gkd-app/src/main/assets/byd/wbsk_tables.json'
asset_target.parent.mkdir(parents=True, exist_ok=True)
shutil.copyfile(asset, asset_target)
hashes['wbsk_tables.json'] = hashlib.sha256(asset.read_bytes()).hexdigest()
(repo / 'docs/byd-wbsk-provenance.json').write_text(json.dumps(hashes, indent=2) + '\n', encoding='utf-8')
print('Ported WBSK codec, transform, tables and asset; no credentials read.')
