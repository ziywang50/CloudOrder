# 重新创建产品（改字段名）
TOKEN=$(curl -s -X POST http://localhost:8088/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "test@example.com", "password": "password123"}' \
  | jq -r '.token')

# 删除旧产品（如果有delete接口）
# 或者直接创建新的

# 产品1 - 改用 "name" 而不是 "productName"
curl -X POST http://localhost:8089/api/products \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "iPhone 15",
    "price": 999.99,
    "stock": 1000,
    "description": "Latest iPhone"
  }'

# 产品2
curl -X POST http://localhost:8089/api/products \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "MacBook Pro",
    "price": 2499.99,
    "stock": 1000,
    "description": "M3 chip"
  }'

# 产品3
curl -X POST http://localhost:8089/api/products \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "AirPods Pro",
    "price": 249.99,
    "stock": 1000,
    "description": "Active noise cancellation"
  }'

# 产品4
curl -X POST http://localhost:8089/api/products \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "iPad Air",
    "price": 599.99,
    "stock": 1000,
    "description": "M2 chip"
  }'

# 产品5（低库存）
curl -X POST http://localhost:8089/api/products \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Apple Watch",
    "price": 399.99,
    "stock": 5,
    "description": "Low stock for testing"
  }'