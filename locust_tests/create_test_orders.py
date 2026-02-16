import requests
import json
import time

BASE_URL = "http://localhost:8090"
AUTH_URL = "http://localhost:8088"
CART_URL = "http://localhost:8083"
PRODUCT_URL = "http://localhost:8089"
NUM_ORDERS = 100

def get_token():
    response = requests.post(f"{AUTH_URL}/api/auth/login", json={
        "email": "test@example.com",
        "password": "password123"
    })
    data = response.json()
    return data['data']['accessToken']

def get_products():
    """获取所有产品"""
    response = requests.get(f"{PRODUCT_URL}/api/products")
    products = response.json()
    # 只取前4个产品（库存充足的）
    return [p['productId'] for p in products if p['stock'] > 100][:4]

def add_to_cart(token, product_id, quantity=1):
    """添加商品到购物车"""
    headers = {"Authorization": f"Bearer {token}"}
    data = {
        "productId": product_id,
        "quantity": quantity
    }
    response = requests.post(f"{CART_URL}/api/cart", headers=headers, json=data)
    return response.status_code == 200

def create_order(token, i):
    """创建订单"""
    headers = {"Authorization": f"Bearer {token}"}
    data = {
        "buyerName": f"Test User {i}",
        "buyerPhone": f"123456{i:04d}",
        "buyerAddress": f"Test Address {i}"
    }
    response = requests.post(f"{BASE_URL}/api/orders", headers=headers, json=data)
    if response.status_code == 200 or response.status_code == 201:
        print(f"✅ Order {i} created")
        return True
    else:
        print(f"❌ Order {i} failed: {response.text}")
        return False

if __name__ == "__main__":
    print("Getting token...")
    token = get_token()
    
    print("Getting products...")
    product_ids = get_products()
    print(f"Available products: {product_ids}")
    
    print(f"\nCreating {NUM_ORDERS} orders...")
    success = 0
    
    for i in range(1, NUM_ORDERS + 1):
        # 随机选择1-2个商品
        import random
        num_items = random.randint(1, 2)
        selected_products = random.sample(product_ids, num_items)
        
        # 添加到购物车
        for product_id in selected_products:
            if not add_to_cart(token, product_id, quantity=1):
                print(f"❌ Failed to add product {product_id} to cart")
                continue
        
        # 创建订单
        if create_order(token, i):
            success += 1
        
        time.sleep(0.2)  # 避免压垮服务
    
    print(f"\n✅ Created {success}/{NUM_ORDERS} orders")