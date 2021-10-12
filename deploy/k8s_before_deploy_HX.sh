kubectl create secret generic properties --from-literal=spring.datasource.url=$hx_db_url \
  --from-literal=spring.datasource.username=$db_user --from-literal=spring.datasource.password=$hx_db_pwd \
  --from-literal=spring.mail.host='hx.smtp.ebi.ac.uk' --from-literal=http.proxySet=true \
  --from-literal=http.proxyHost=hx-wwwcache.ebi.ac.uk --from-literal=http.proxyPort=3128 \
  --from-literal=https.proxyHost=hx-wwwcache.ebi.ac.uk --from-literal=https.proxyPort=3128 \
  --from-literal=http.nonProxyHosts='localhost|127.0.0.1|.cluster.local' --from-literal=https.proxySet=true \
  --dry-run=client -o yaml | kubectl apply -f -