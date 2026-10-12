curl -v -H 'Contcurl -v -H 'Content-Type: application/json' -d '{}' \
 http://localhost:8080/api/calibracoes/processamento

- Host localhost:8080 was resolved.
- IPv6: ::1
- IPv4: 127.0.0.1
- Trying [::1]:8080...
- Connected to localhost (::1) port 8080
  > POST /api/calibracoes/processamento HTTP/1.1
  > Host: localhost:8080
  > User-Agent: curl/8.5.0
  > Accept: _/_
  > Content-Type: application/json
  > Content-Length: 2
  >
  > < HTTP/1.1 415
  > < Accept: multipart/form-data
  > < Content-Type: application/problem+json
  > < Transfer-Encoding: chunked
  > < Date: Mon, 12 Oct 2026 01:50:15 GMT
  > <
- Connection #0 to host localhost left intact
  {"detail":"Envie a planilha .xlsx como multipart/form-data, no campo 'arquivo'.","instance":"/api/calibracoes/processamento","status":415,"title":"Unsupported Media Type"}
