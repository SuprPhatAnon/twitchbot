# Deployment Guide

The application can be deployed using Docker, Docker Compose, or Kubernetes.

## Prerequisites

- [Docker](https://docs.docker.com/get-docker/) and [Docker Compose](https://docs.docker.com/compose/install/).
- A MariaDB database.
- Twitch Developer account credentials.

## Environment Variables

| Variable | Description | Default |
| --- | --- | --- |
| `SONG_UPLOAD_PATH` | Path for uploaded songs | `/uploads/songs` |
| `DB_HOST` | MariaDB host | `localhost` |
| `DB_USER` | MariaDB username | `mariadb` |
| `DB_PASSWORD` | MariaDB password | `mariadb` |
| `TWITCH_REDIRECT_URI_HOST` | OAuth redirect URI host | `https://music.phat.wtf` |

## Docker Compose

1. Clone the repository.
2. Create a `.env` file with your credentials.
3. Start the containers:
   ```bash
   docker-compose up -d
   ```

## Kubernetes

Detailed instructions for Kubernetes deployments can be found in the following files:

- [General Kubernetes Guide](../k8s/README.md)
- [Minikube Deployment](../k8s/MINIKUBE.md)
- [k3s Deployment](../k8s/K3S.md)

The deployment uses Kustomize for managing different environment overlays (production, minikube, k3s) located in `k8s/overlays/`.
