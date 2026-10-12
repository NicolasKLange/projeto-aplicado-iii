curl -i -H 'Expect:' -F "arquivo=@$HOME/teste-413/limite.xlsx" \
 http://localhost:8080/api/calibracoes/processamento
HTTP/1.1 413
Content-Type: application/problem+json
Transfer-Encoding: chunked
Date: Mon, 12 Oct 2026 01:38:19 GMT
Connection: close

{"detail":"O arquivo excede o limite de 5 MB.","instance":"/api/calibracoes/processamento","status":413,"title":"Content Too Large"}
