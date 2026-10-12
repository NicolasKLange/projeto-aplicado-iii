curl -i -H stat -c %s 'Expect:' -F "arquivo=@$HOME/teste-413/exato-5mb.xlsx" http://localhost:8080/api/cal
ibracoes/processamento

HTTP/1.1 400
Content-Type: application/problem+json
Transfer-Encoding: chunked
Date: Mon, 12 Oct 2026 01:37:29 GMT
Connection: close

{"detail":"A planilha não cumpre o contrato. Corrija os erros listados e envie de novo.","instance":"/api/calibracoes/processamento","status":400,"title":"Bad Request","erros":[{"linha":null,"coluna":null,"regra":"R02","mensagem":"O arquivo não está em formato xlsx. Salve como 'Pasta de Trabalho do Excel (.xlsx)'."}]}
