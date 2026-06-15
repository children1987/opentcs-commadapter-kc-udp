import zipfile, os

adapter = os.path.dirname(os.path.abspath(__file__))
build = os.path.join(adapter, 'build', 'classes_v2')
out = os.path.join(adapter, 'build', 'kecong-opentcs-adapter-1.0.0.jar')

if os.path.exists(out):
    os.remove(out)

# Create META-INF
meta = os.path.join(build, 'META-INF', 'services')
os.makedirs(meta, exist_ok=True)
svc = os.path.join(meta, 'org.opentcs.customizations.kernel.KernelInjectionModule')
with open(svc, 'w') as f:
    f.write('com.kecong.opentcs.KecongAdapterModule')

# Package as JAR
with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as zf:
    for root, dirs, files in os.walk(build):
        for fn in files:
            fp = os.path.join(root, fn)
            arc = os.path.relpath(fp, build).replace('\\', '/')
            zf.write(fp, arc)

print('JAR created:', os.path.getsize(out), 'bytes')
# Verify
with zipfile.ZipFile(out, 'r') as zf:
    for name in zf.namelist():
        if 'class' in name or 'META' in name:
            print(' ', name)
