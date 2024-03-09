SHELL!=which bash
deployment_stage = ${DEPLOYMENT_ENV}

deploy-backend-services-%:
	$(MAKE) set-context-$(*)
	$(MAKE) install-infra-helm-chart-rabbit
	$(MAKE) install-infra-helm-chart-redis

set-context-%:
	$(MAKE) init-$(*)
	kubectx ${K8S_CLUSTER}
	kubens ${K8S_NAMESPACE}

init-%:
	. ./config/env_$(*)
	kubectl create namespace ${K8S_NAMESPACE} || { echo '$K8S_NAMESPACE namespace exists';}

install-infra-helm-chart-%:
	cd helm-charts && helm dependency update $(*) && helm package $(*)
	. config/env_$(deployment_stage) && cd helm-charts && helm upgrade --values $(*)/values.yaml --values environments/$(deployment_stage).yaml $(*) ./$(*) --force --install
	cd helm-charts && rm *.tgz

