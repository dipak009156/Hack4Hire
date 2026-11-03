from test_suite import request

_, auth = request('POST', '/auth/login', {'username': 'admin', 'password': 'password123', 'tenant': 'tenant-alpha'})
token = auth['accessToken']
_, created = request('POST', '/policies', {'name': 'TestPol', 'expression': 'dept == 1'}, token=token)
print('Created policy:', created)
pid = created["id"]
ver = created["version"]
_, updated = request('PUT', f'/policies/{pid}', {'name': 'TestPol2', 'expression': 'dept == 2', 'version': ver}, token=token)
print('Updated policy:', updated)
print('ver comparison:', ver, '->', updated['version'])

