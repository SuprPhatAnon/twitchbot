# Running on Minikube

This guide provides step-by-step instructions for deploying the Twitch Song Overlay Bot to a local [Minikube](https://minikube.sigs.k8s.io/) cluster.

## Prerequisites

-   [Minikube](https://minikube.sigs.k8s.io/docs/start/) installed.
-   [kubectl](https://kubernetes.io/docs/tasks/tools/) installed.
-   [Docker](https://www.docker.com/) installed and running.

## Step-by-Step Deployment

### 1. Start Minikube

Start your Minikube cluster and enable the Ingress addon:

```bash
minikube start
minikube addons enable ingress
```

### 2. Build and Manage the Docker Image

Minikube has its own internal Docker repository. You must make the image available to the cluster.

#### Option A: Build Directly in Minikube (Recommended)

Point your shell's Docker CLI to Minikube's built-in Docker daemon. This builds the image directly into Minikube's repository:

```bash
# Point your shell to use minikube's docker daemon
eval $(minikube docker-env)

# Build the image directly into minikube
docker build -t twitchbot-app:latest .
```

#### Option B: Load from Local Docker Repository

If you built the image using your system's Docker daemon, you can "push" (load) it into Minikube's repository:

```bash
# Build the image locally (on your host)
docker build -t twitchbot-app:latest .

# Load (push) the image into minikube's repository
minikube image load twitchbot-app:latest
```

You can verify the image is in the repository with:
```bash
minikube image ls | grep twitchbot-app
```

#### Using an External Registry (Docker Hub, etc.)

Alternatively, you can push your image to a registry and update the deployment manifest.

1.  **Push to a registry:**
    ```bash
    docker build -t your-username/twitchbot-app:latest .
    docker push your-username/twitchbot-app:latest
    ```
2.  **Update Kustomize:**
    In `base/kustomization.yaml`, add an image override:
    ```yaml
    images:
      - name: twitchbot-app
        newName: your-username/twitchbot-app
        newTag: latest
    ```

### 3. Configure Secrets

The application requires some configuration for database passwords and API keys. These are managed via Kustomize in `k8s/base/kustomization.yaml`.

Open `base/kustomization.yaml` and update the `secretGenerator` values as needed.

```yaml
secretGenerator:
  - name: app-secrets
    literals:
      - API_KEY=your_secret_key # Change this!
  - name: db-secrets
    literals:
      - mariadb-root-password=your_db_password # Change this!
```

### 4. Deploy to Kubernetes

Apply the Kustomize manifests using the `minikube` overlay:

```bash
kubectl apply -k overlays/minikube/
```

This will create a `streaming` namespace and deploy MariaDB and the Twitch Bot application.

### 5. Access the Application

The default Ingress configuration uses the host `stream.phat.wtf`. To access it locally:

#### Option A: Map Minikube IP to `/etc/hosts`

1.  Get the Minikube IP:
    ```bash
    minikube ip
    ```
2.  Add an entry to your `/etc/hosts` (or `C:\Windows\System32\drivers\etc\hosts` on Windows):
    ```text
    <MINIKUBE_IP> stream.phat.wtf
    ```
3.  Run a tunnel to expose the Ingress controller:
    ```bash
    minikube tunnel
    ```

#### Option B: Access via Port-Forwarding (Direct Access)

If you don't want to mess with hostnames, you can port-forward the service directly:

```bash
kubectl port-forward -n streaming svc/app 8080:8080
```
Then access the app at `http://localhost:8080`.

## Troubleshooting

-   **Check Pods:** `kubectl get pods -n streaming`
-   **Check Logs:** `kubectl logs -n streaming deployment/app`
-   **MariaDB Connection:** The app might take a minute to start as it waits for MariaDB to be ready via an `initContainer`.
-   **Ingress Issues:** Ensure `minikube tunnel` is running if you are using the Ingress hostname.
