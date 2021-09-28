kubectl create secret generic properties --from-literal=spring.datasource.url=$hh_db_url \
  --from-literal=spring.datasource.username=$db_user --from-literal=spring.datasource.password=$db_pwd \
  --from-literal=spring.mail.host='hh.smtp.ebi.ac.uk' --dry-run=client -o yaml | kubectl apply -f -