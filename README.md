# Code Migration Demo - He thong Phan tan

Demo mo phong di tru ma trong he thong phan tan voi 5 nodes.

## Cau truc du an

```
HTPT/
├── server/          # Backend - Spring Boot
├── client/          # Frontend - React
└── README.md
```

## Yeu cau

- Java 17+
- Node.js 18+
- Maven 3.8+

## Huong dan chay

### 1. Backend (Spring Boot)

```bash
cd server

# Build project
mvn clean package -DskipTests

# Chay Coordinator (Port 8080)
java -jar target/code-migration-1.0.0.jar
```

### 2. Frontend (React)

```bash
cd client

# Cai dat dependencies
npm install

# Chay development server
npm run dev
```

Truy cap: http://localhost:3000

### 3. Chay Worker Nodes (Demo tren nhieu may)

Tren moi may, chay:

```bash
# May 2
java -jar code-migration-1.0.0.jar --spring.profiles.active=node2

# May 3
java -jar code-migration-1.0.0.jar --spring.profiles.active=node3

# May 4
java -jar code-migration-1.0.0.jar --spring.profiles.active=node4

# May 5
java -jar code-migration-1.0.0.jar --spring.profiles.active=node5
```

## Demo Scenarios

### 1. Mobile Agent
Di chuyen agent qua cac nodes de thu thap du lieu.

### 2. Load Balancing
Tu dong di tru khi node qua tai.

### 3. Fault Tolerance
Di tru khi node sap fail.

## API Endpoints

- `GET /api/nodes` - Danh sach nodes
- `GET /api/nodes/topology` - Topology mang
- `POST /api/migrations` - Khoi tao di tru
- `POST /api/code` - Upload code

## WebSocket Events

- `/topic/nodes` - Cap nhat nodes
- `/topic/migrations` - Cap nhat migrations
- `/topic/migration/{id}` - Tien do migration

## Cau hinh mang (5 may)

Chinh sua file `application.yml`:

```yaml
node:
  coordinator-url: http://192.168.1.101:8080
```


## License

MIT
