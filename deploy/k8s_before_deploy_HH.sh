kubectl create secret generic properties --from-literal=spring.datasource.url=$hh_db_url \
  --from-literal=spring.datasource.username=$db_user --from-literal=spring.datasource.password=$hh_db_pwd \
  --from-literal=spring.mail.host='hh.smtp.ebi.ac.uk' --from-literal=no_proxy=localhost,.cluster.local,127.0.0.1 \
  --from-literal=http_proxy=http://hh-wwwcache.ebi.ac.uk:3128 --from-literal=https_proxy=http://hh-wwwcache.ebi.ac.uk:3128  \
  --dry-run=client -o yaml | kubectl apply -f -