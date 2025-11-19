# Hướng dẫn Demo với Docker

## Yêu cầu

- Docker Desktop
- Docker Compose

## Cách chạy

### 1. Build và chạy tất cả services

```bash
cd d:/work-space/HTPT

# Build và chạy
docker-compose up --build

# Hoặc chạy ở background
docker-compose up --build -d
```

### 2. Truy cập

- **Frontend**: http://localhost:3000
- **Coordinator API**: http://localhost:8080

### 3. Kiểm tra các nodes

```bash
# Xem logs tất cả services
docker-compose logs -f

# Xem logs của node cụ thể
docker-compose logs -f node2

# Xem trạng thái containers
docker-compose ps
```

### 4. Demo Migration

1. Mở browser: http://localhost:3000
2. Bạn sẽ thấy 5 nodes hiển thị trên Dashboard
3. Sử dụng **Control Panel** để:
   - Click "Mobile Agent" để demo di trú agent
   - Click "Load Balancing" để demo cân bằng tải
   - Click "Fault Tolerance" để demo chịu lỗi
4. Hoặc tự tạo migration:
   - Chọn Source Node
   - Chọn Target Node
   - Chọn Migration Type (Weak/Strong)
   - Click "Start Migration"

### 5. Dừng hệ thống

```bash
# Dừng tất cả
docker-compose down

# Dừng và xóa volumes
docker-compose down -v
```

## Kiến trúc Docker

```
┌─────────────────────────────────────────────┐
│           Docker Network                     │
│                                             │
│  ┌───────────┐     ┌───────────┐            │
│  │ Frontend  │────▶│Coordinator│            │
│  │  :3000    │     │   :8080   │            │
│  └───────────┘     └─────┬─────┘            │
│                          │                  │
│         ┌────────────────┼────────────────┐ │
│         │                │                │ │
│    ┌────┴────┐     ┌────┴────┐     ┌────┴────┐
│    │ Node 2  │     │ Node 3  │     │ Node 4  │...
│    │  :8082  │     │  :8083  │     │  :8084  │
│    └─────────┘     └─────────┘     └─────────┘
│                                             │
└─────────────────────────────────────────────┘
```

## Troubleshooting

### Lỗi build
```bash
# Xóa cache và build lại
docker-compose build --no-cache
```

### Lỗi network
```bash
# Xóa network cũ
docker network prune

# Chạy lại
docker-compose up --build
```

### Xem logs chi tiết
```bash
docker-compose logs coordinator
docker-compose logs node2
docker-compose logs frontend
```

## Ports

| Service | Internal Port | External Port |
|---------|--------------|---------------|
| Coordinator | 8080 | 8080 |
| Node 2 | 8080 | 8082 |
| Node 3 | 8080 | 8083 |
| Node 4 | 8080 | 8084 |
| Node 5 | 8080 | 8085 |
| Frontend | 80 | 3000 |
