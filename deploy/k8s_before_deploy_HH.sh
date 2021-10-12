kubectl create secret generic properties --from-literal=spring.datasource.url=$hh_db_url \
  --from-literal=spring.datasource.username=$db_user --from-literal=spring.datasource.password=$hh_db_pwd \
  --from-literal=spring.mail.host='hh.smtp.ebi.ac.uk' \
  --from-literal=JAVA_TOOL_OPTIONS=" -Dhttp.proxySet=true -Dhttp.proxyHost=hh-wwwcache.ebi.ac.uk -Dhttp.proxyPort=3128 -Dhttps.proxySet=true -Dhttps.proxyHost=hh-wwwcache.ebi.ac.uk -Dhttps.proxyPort=3128 -Dhttp.nonProxyHosts='localhost|127.0.0.1|.cluster.local' " \
  --dry-run=client -o yaml | kubectl apply -f -