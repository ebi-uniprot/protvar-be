kubectl create secret generic properties --from-literal=spring.datasource.url=$hx_db_url \
  --from-literal=spring.datasource.username=$db_user --from-literal=spring.datasource.password=$hx_db_pwd \
  --from-literal=spring.mail.host='hx.smtp.ebi.ac.uk' --from-literal=no_proxy=localhost,.cluster.local,127.0.0.1 \
  --from-literal=http_proxy=http://hx-wwwcache.ebi.ac.uk:3128 --from-literal=https_proxy=http://hx-wwwcache.ebi.ac.uk:3128  \
  --dry-run=client -o yaml | kubectl apply -f -