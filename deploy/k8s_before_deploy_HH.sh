kubectl create secret generic properties --from-literal=spring.datasource.url=$hh_db_url \
  --from-literal=spring.datasource.username=$db_user --from-literal=spring.datasource.password=$hh_db_pwd \
  --from-literal=spring.mail.host='hh.smtp.ebi.ac.uk' --from-literal=http.proxySet=true \
  --from-literal=http.proxyHost=hh-wwwcache.ebi.ac.uk --from-literal=http.proxyPort=3128 \
  --from-literal=https.proxyHost=hh-wwwcache.ebi.ac.uk --from-literal=https.proxyPort=3128 \
  --from-literal=http.nonProxyHosts='localhost|127.0.0.1|.cluster.local' --from-literal=https.proxySet=true \
  --dry-run=client -o yaml | kubectl apply -f -