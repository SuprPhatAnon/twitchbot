# Running on k3s

This guide provides instructions for deploying the Twitch Song Overlay Bot to a [k3s](https://k3s.io/) cluster.

## Prerequisites

-   A running [k3s](https://docs.k3s.io/quick-start) cluster.
-   [kubectl](https://kubernetes.io/docs/tasks/tools/) configured to point to your k3s cluster (usually `/etc/rancher/k3s/k3s.yaml`).
-   [Docker](https://www.docker.com/) or another container runtime.

## Step-by-Step Deployment

### 1. Build and Import into the Image Repository

k3s uses `containerd` as its default container runtime and doesn't have a direct `docker-env` equivalent. You must build your image and manually import it into k3s's internal repository.

#### Using Docker and `k3s ctr`

```bash
# Build the image locally (on your host machine)
docker build -t twitchbot-app:latest .

# Export and "push" into k3s repository (containerd)
docker save twitchbot-app:latest | sudo k3s ctr images import -
```

Check if the image was successfully imported:
```bash
sudo k3s ctr images ls | grep twitchbot-app
```

#### Using an External Registry (Docker Hub, GitHub Registry, etc.)

Alternatively, you can push your image to a public or private registry and update the deployment manifest.

1.  **Push to a registry:**
    ```bash
    docker build -t your-username/twitchbot-app:latest .
    docker push your-username/twitchbot-app:latest
    ```
2.  **Update Kustomize:**
    In `kustomization.yaml`, add an image override:
    ```yaml
    images:
      - name: twitchbot-app
        newName: your-username/twitchbot-app
        newTag: latest
    ```

### 2. Configure Secrets

The application requires configuration for database passwords and API keys, managed via Kustomize in `base/kustomization.yaml`.

Update the `secretGenerator` values in `base/kustomization.yaml`. Note that the application now uses RBAC (Role-Based Access Control) via Spring Security, but you can still provide an initial admin password if needed (default is `admin`/`admin` if not changed):

```yaml
secretGenerator:
  - name: twitchbotapp-secrets
    literals:
      - DB_PASSWORD=your_db_password
  - name: db-secrets
    literals:
      - mariadb-root-password=your_db_password
```

### 3. Deploy to k3s

Apply the Kustomize manifests using the `k3s` overlay:

```bash
kubectl apply -k overlays/k3s/
```

This will automatically adjust the ingress for Traefik (the default k3s Ingress controller).

### 4. Ingress Configuration

The `k3s` overlay automatically configures the Ingress for **Traefik** (k3s's default Ingress controller) by:
-   Updating the host to `music.phat.wtf`.
-   Enabling TLS via Traefik annotations.
-   Configuring `cert-manager` with the `letsencrypt-prod` cluster issuer.
-   Setting up the `tls` section with the secret `music-phat-wtf-tls`.

If you are using a different Ingress controller or a different cluster issuer, you might need to adjust the overlay in `overlays/k3s/kustomization.yaml`.

### 5. Accessing the App

If k3s is on a remote server, ensure your DNS or `/etc/hosts` points `music.phat.wtf` to the server's IP.

---

## Accessing via Apache2 Proxy (Dual-Cluster Setup)

If you have both **Minikube** and **k3s** running and want to access them through a single Apache2 server, follow these steps.

### Apache2 Configuration

Enable the required modules:

```bash
sudo a2enmod proxy proxy_http proxy_wstunnel rewrite headers
```

Create a virtual host configuration (e.g., `/etc/apache2/sites-available/twitchbot-proxy.conf`):

```apache
<VirtualHost *:80>
    ServerName minikube.music.phat.wtf

    # WebSocket Proxy (for SockJS/STOMP)
    ProxyPass /ws/ ws://<MINIKUBE_IP>/ws/
    ProxyPassReverse /ws/ ws://<MINIKUBE_IP>/ws/

    # Standard HTTP Proxy
    ProxyPass / http://<MINIKUBE_IP>/
    ProxyPassReverse / http://<MINIKUBE_IP>/

    ProxyPreserveHost On
</VirtualHost>

<VirtualHost *:443>
    ServerName music.phat.wtf

    # WebSocket Proxy (for SockJS/STOMP)
    ProxyPass /ws/ ws://<K3S_SERVER_IP>/ws/
    ProxyPassReverse /ws/ ws://<K3S_SERVER_IP>/ws/

    # Standard HTTP Proxy
    ProxyPass / http://<K3S_SERVER_IP>/
    ProxyPassReverse / http://<K3S_SERVER_IP>/

    ProxyPreserveHost On

    # Add your SSL configuration here if Apache handles SSL termination
</VirtualHost>
```

**Notes:**
- Replace `<MINIKUBE_IP>` with the output of `minikube ip`.
- Replace `<K3S_SERVER_IP>` with your k3s node IP.
- Ensure `ProxyPreserveHost On` is set so the Ingress controllers can route by hostname.
- You'll need to update the `host` in `ingress.yaml` for each cluster to match these ServerNames or use a wildcard.

### DNS / Hosts file

Update your local machine's `/etc/hosts`:

```text
<APACHE_SERVER_IP> minikube.music.phat.wtf
<APACHE_SERVER_IP> music.phat.wtf
```
