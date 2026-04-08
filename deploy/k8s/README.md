# Kubernetes 演示清单（学习用）

本目录提供最小可跑的 **PostgreSQL（pgvector）+ Vagent** 示例，便于理解 K8s 中 **Deployment / Service / Secret / Namespace** 的关系。

## 使用前必读

1. 修改 **`postgres-secret`**、**`vagent-app-secret`** 中的占位密码与 **JWT 密钥**（至少 32 字符）。勿将真实密钥提交到公开仓库。  
2. 在**仓库根目录**构建：`mvn -DskipTests package` 后执行 `docker build -t vagent:local .`。  
3. **minikube / kind**：将 `vagent.yaml` 中 `imagePullPolicy` 改为 `Never`，并用 `minikube image load` 等方式把本机构建的 `vagent:local` 载入节点。  
4. **云集群**：把 `vagent.yaml` 里的 `image` 换成你的镜像仓库地址，`imagePullPolicy` 用 `IfNotPresent` 或 `Always`。

## 应用

```bash
kubectl apply -f deploy/k8s/
kubectl -n vagent-demo wait --for=condition=available deployment/postgres --timeout=120s
kubectl -n vagent-demo wait --for=condition=available deployment/vagent --timeout=180s
kubectl -n vagent-demo port-forward svc/vagent 8080:8080
```

浏览器访问 `http://localhost:8080/`。数据库与 Flyway 在 Vagent 首次启动时自动迁移。

## 清理

```bash
kubectl delete namespace vagent-demo
```
