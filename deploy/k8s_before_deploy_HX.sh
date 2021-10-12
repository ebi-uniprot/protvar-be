jvm="-Dhttp.proxyHost=hx-wwwcache.ebi.ac.uk -Dhttp.proxyPort=3128 -Dhttp.proxySet=true"
jvm+=" -Dhttps.proxyHost=hx-wwwcache.ebi.ac.uk -Dhttps.proxyPort=3128 -Dhttps.proxySet=true"
jvm+=" -Dhttp.nonProxyHosts=localhost|127.0.0.1|.cluster.local"
kubectl create secret generic properties --from-literal=spring.datasource.url=$hx_db_url \
  --from-literal=spring.datasource.username=$db_user --from-literal=spring.datasource.password=$hx_db_pwd \
  --from-literal=spring.mail.host='hx-smtp.ebi.ac.uk' --from-literal=JAVA_TOOL_OPTIONS="${jvm}" \
  --dry-run=client -o yaml | kubectl apply -f -