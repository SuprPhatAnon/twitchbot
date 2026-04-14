# Kubernetes Deploy Package

This package contains Kubernetes manifests for the Twitch Bot application, based on the `docker-compose.yml` configuration.

## Structure

```text
k8s/
└── base/
    ├── app/
    │   ├── app.yaml             # Deployment and Service for the Spring Boot app
    │   └── ingress.yaml         # Ingress resource for the app
    ├── mariadb/
    │   └── mariadb.yaml         # Deployment, Service, and PVC for MariaDB
    └── kustomization.yaml       # Kustomize base configuration (ConfigMaps & Secrets)
```

## Deployment

To deploy the application to your Kubernetes cluster:

1.  **Customize Secrets:**
    Open `k8s/base/kustomization.yaml` and update the `secretGenerator` literals with your actual Twitch API credentials and desired database password.

2.  **Apply Manifests:**
    Using `kubectl`, apply the kustomization:

    ```bash
    kubectl apply -k k8s/base/
    ```

## Key Features

- **Health Probes:** Both `mariadb` and `app` have liveness and readiness probes.
- **Dependency Management:** The `app` deployment uses an `initContainer` to wait for the `mariadb` service to be reachable before starting.
- **Persistence:** `mariadb` uses a `PersistentVolumeClaim` (PVC) for database storage.
- **Config Management:** Environment variables are managed via `ConfigMap` and `Secret` generators in `kustomization.yaml`.

## Important Notes

- **Docker Images:** The deployment manifests use placeholder image tags (e.g., `twitchbot-app:latest`). Ensure you build and push these images to your registry and update the image names in the YAML files or via Kustomize overlays.
- **Resources:** Default resource requests/limits are not set; consider adding them based on your cluster's capacity.
- **Ingress:** This package includes an Ingress resource configured for `stream.phat.wtf`. Ensure you have an Ingress controller (e.g., NGINX) and `cert-manager` installed for TLS.
