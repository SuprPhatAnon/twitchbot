# Kubernetes Deploy Package

This package contains Kubernetes manifests for the Twitch Bot application, based on the `docker-compose.yml` configuration.

## Structure

```text
k8s/
├── base/
│   ├── twitchbotapp/
│   │   ├── twitchbotapp.yaml    # Deployment and Service for the Spring Boot app
│   │   ├── pvc.yaml             # PersistentVolumeClaim for song uploads
│   │   └── ingress.yaml         # Ingress resource for the app
│   ├── mariadb/
│   │   └── mariadb.yaml         # Deployment, Service, and PVC for MariaDB
│   ├── kustomization.yaml       # Kustomize base configuration
│   └── namespace.yaml           # Namespace definition
└── overlays/
    ├── production/              # Base configuration for standard Kubernetes
    ├── minikube/                # Overlays for local Minikube development
    └── k3s/                     # Overlays for k3s clusters (Traefik ingress)
```

## Deployment

To deploy the application to your Kubernetes cluster:

1.  **Customize Secrets:**
    Open `k8s/base/kustomization.yaml` and update the `secretGenerator` literals with your actual Twitch API credentials and desired database password.

2.  **Apply Manifests:**
    Choose the overlay that matches your environment and use `kubectl` to apply it.

    **For standard Kubernetes (Production):**
    ```bash
    kubectl apply -k k8s/overlays/production/
    ```

    **For Minikube:**
    ```bash
    kubectl apply -k k8s/overlays/minikube/
    ```

    **For k3s:**
    ```bash
    kubectl apply -k k8s/overlays/k3s/
    ```

## Key Features

- **Health Probes:** Both `mariadb` and `twitchbotapp` have liveness and readiness probes.
- **Dependency Management:** The `twitchbotapp` deployment uses an `initContainer` to wait for the `mariadb` service to be reachable before starting.
- **Persistence:** Both `mariadb` and `twitchbotapp` (for song uploads) use `PersistentVolumeClaims` (PVC) for storage.
- **Config Management:** Environment variables are managed via `ConfigMap` and `Secret` generators in `kustomization.yaml`. This includes the `API_KEY` for general access and the `UPLOAD_API_KEY` for song uploads.

## Important Notes

- **Docker Images:** The deployment manifests use placeholder image tags (e.g., `twitchbot-app:latest`). Ensure you build and push these images to your registry and update the image names in the YAML files or via Kustomize overlays.
- **Resources:** Default resource requests/limits are not set; consider adding them based on your cluster's capacity.
- **Ingress:** This package includes an Ingress resource configured for `stream.phat.wtf`. Ensure you have an Ingress controller (e.g., NGINX) installed.
- **Minikube:** For local testing with Minikube, see the [Minikube Deployment Guide](MINIKUBE.md).

## Persistence Configuration

By default, the `PersistentVolumeClaim` (PVC) for song uploads is configured to use a `PersistentVolume` (PV) that specifies a `hostPath`. This is convenient for development and single-node setups.

### Using a Specific Host Path

The storage for song uploads is mapped to a directory on your host machine.

1.  Edit `k8s/base/twitchbotapp/pvc.yaml`.
2.  Set `hostPath.path` to your desired directory (default: `/mnt/data/songs`).
3.  Re-apply the manifests: `kubectl apply -k k8s/overlays/<overlay>/`.

If your environment (like a managed Kubernetes service) supports dynamic provisioning and you don't want to use `hostPath`, you should:
1. Remove the `PersistentVolume` (lines 1-12) from `pvc.yaml`.
2. Remove `storageClassName: manual` and `volumeName: song-uploads-pv` from the `PersistentVolumeClaim` in `pvc.yaml`.

## Specific Guides

- [Minikube Deployment Guide](MINIKUBE.md)
- [k3s Deployment Guide](K3S.md)
