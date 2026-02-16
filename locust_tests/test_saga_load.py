# test_saga_precise.py
from locust import HttpUser, task, between, events
import random, time, requests, uuid, threading

# ---------- 1. 全局账本 ----------
order_journal = {}          # key=orderId, value=dict
saga_stats = {
    'total_orders': 0,
    'high_stock_orders': 0,
    'low_stock_orders': 0,
    'errors': 0
}

def retry_request(method, url, max_retries=3, **kwargs):
    """带重试的HTTP请求"""
    for attempt in range(max_retries):
        try:
            resp = method(url, **kwargs)
            return resp
        except (requests.exceptions.Timeout, requests.exceptions.ConnectionError) as e:
            if attempt < max_retries - 1:
                wait_time = (attempt + 1) * 2  # 2秒, 4秒, 6秒
                print(f"   Retry {attempt+1}/{max_retries} after {wait_time}s... ({e})")
                time.sleep(wait_time)
            else:
                print(f"   Failed after {max_retries} retries: {e}")
                raise
        except Exception as e:
            print(f"   Unexpected error: {e}")
            raise

PRODUCTS_HIGH_STOCK = []
PRODUCTS_LOW_STOCK = []
TEST_USERS = [
    {"email": f"test{i}@example.com", "password": "password123"}
    for i in range(1, 51)
]
AUTH_HOST = "http://localhost:8088"
CART_HOST = "http://localhost:8083"
ORDER_COMMAND_HOST = "http://localhost:8090"
ORDER_QUERY_HOST = "http://localhost:8091"
PRODUCT_HOST = "http://localhost:8089"

@events.test_start.add_listener
def on_test_start(environment, **kwargs):
    global PRODUCTS_HIGH_STOCK, PRODUCTS_LOW_STOCK
    
    print("🚀 Creating 50 test users...")
    print("="*60)
    
    success_count = 0
    existing_count = 0
    
    for i in range(1, 51):
        user_data = {
            "email": f"test{i}@example.com",
            "password": "password123",
            "name": f"Test User {i}"
        }
        
        try:
            # ⭐ 使用retry
            response = retry_request(
                requests.post,
                f"{AUTH_HOST}/api/auth/signup",
                max_retries=3,
                json=user_data,
                timeout=10  # 增加timeout到10秒
            )
            
            if response.status_code in [200, 201]:
                if i <= 5:  # 只打印前5个
                    print(f"✅ [{i}/50] Created: test{i}@example.com")
                success_count += 1
            elif response.status_code in [400, 409]:
                existing_count += 1
                
        except Exception as e:
            if i <= 5:
                print(f"❌ [{i}/50] Failed after retries: {e}")
        
        # 进度提示
        if i % 10 == 0:
            print(f"  Progress: {i}/50 ({success_count + existing_count} ready)")
        
        time.sleep(0.1)  # 减少延迟
    
    print(f"\n✅ Users ready: {success_count + existing_count}/50")
    print("="*60)
    
    # 登录（带retry）
    print("\n🔐 Logging in...")
    user_creds = {"email": "test1@example.com", "password": "password123"}
    
    try:
        resp = retry_request(
            requests.post,
            f"{AUTH_HOST}/api/auth/login",
            max_retries=3,
            json=user_creds,
            timeout=10
        )
        
        if resp.status_code != 200:
            print(f"  ❌ Login failed: {resp.status_code}")
            return
        
        token = resp.json()['data']['accessToken']
        headers = {"Authorization": f"Bearer {token}"}
        print(f"  ✅ Login OK")
        
    except Exception as e:
        print(f"  ❌ Login failed after retries: {e}")
        return
    
    # 创建产品（带retry）
    print("\n📦 Creating HIGH stock products...")
    for i in range(1, 4):
        try:
            resp = retry_request(
                requests.post,
                f"{PRODUCT_HOST}/api/products",
                max_retries=3,
                json={
                    "name": f"High Stock iPhone {i}",
                    "description": "High stock test",
                    "price": 1000.0,
                    "stock": 5000
                },
                headers=headers,
                timeout=10
            )
            
            if resp.status_code in [200, 201]:
                data = resp.json()
                product_id = data.get('productId') or data.get('data', {}).get('productId')
                if product_id:
                    PRODUCTS_HIGH_STOCK.append(str(product_id))
                    print(f"  ✅ [{i}/3] Created: {product_id}")
                    
        except Exception as e:
            print(f"  ❌ [{i}/3] Failed after retries: {e}")
        
        time.sleep(0.2)
    
    print("\n📦 Creating LOW stock products...")
    for i in range(1, 4):
        try:
            resp = retry_request(
                requests.post,
                f"{PRODUCT_HOST}/api/products",
                max_retries=3,
                json={
                    "name": f"Low Stock iPhone {i}",
                    "description": "Low stock test",
                    "price": 500.0,
                    "stock": 5
                },
                headers=headers,
                timeout=10
            )
            
            if resp.status_code in [200, 201]:
                data = resp.json()
                product_id = data.get('productId') or data.get('data', {}).get('productId')
                if product_id:
                    PRODUCTS_LOW_STOCK.append(str(product_id))
                    print(f"  ✅ [{i}/3] Created: {product_id}")
                    
        except Exception as e:
            print(f"  ❌ [{i}/3] Failed after retries: {e}")
        
        time.sleep(0.2)
    
    # 最终结果
    print(f"\n" + "="*70)
    print(f"📊 SETUP RESULTS:")
    print(f"  HIGH: {PRODUCTS_HIGH_STOCK}")
    print(f"  LOW: {PRODUCTS_LOW_STOCK}")
    print(f"  Counts: {len(PRODUCTS_HIGH_STOCK)} high, {len(PRODUCTS_LOW_STOCK)} low")
    
    if len(PRODUCTS_HIGH_STOCK) >= 2 and len(PRODUCTS_LOW_STOCK) >= 2:
        print(f"\n✅ SETUP OK (at least 2 products each)")
    else:
        print(f"\n❌ SETUP FAILED (not enough products)")
    
    print("="*70 + "\n")

# ---------- 2. 业务压测 ----------
class SagaTestUser(HttpUser):
    wait_time = between(0.1, 0.5)
    host = ORDER_COMMAND_HOST
    
    _user_index = 0
    #Lock to prevent user_index being modified
    _user_lock = threading.Lock()
    
    def on_start(self):
        # 每个用户获取唯一的索引
        with SagaTestUser._user_lock:
            my_index = SagaTestUser._user_index
            SagaTestUser._user_index += 1
        
        # 使用这个唯一索引
        if my_index >= len(TEST_USERS):
            my_index = my_index % len(TEST_USERS)  # 循环使用
        
        self.user_creds = TEST_USERS[my_index]  # 不用random
        
        resp = self.client.post(
            f"{AUTH_HOST}/api/auth/login",
            json=self.user_creds
        )
        if resp.status_code != 200:
            saga_stats['errors'] += 1
            return
        self.token = resp.json()['data']['accessToken']
        self.headers = {"Authorization": f"Bearer {self.token}"}
        
        print(f"✅ User {my_index+1} logged in as {self.user_creds['email']}")  # Debug

    # --- 高库存任务 ---
    @task(50)
    def test_high_stock_order(self):
        print("HIGH STOCK user test started")
        print(f"\n🔍 DEBUG in task:")
        print(f"  PRODUCTS_HIGH_STOCK = {PRODUCTS_HIGH_STOCK}")
        print(f"  PRODUCTS_LOW_STOCK = {PRODUCTS_LOW_STOCK}")
        print(f"  len(HIGH) = {len(PRODUCTS_HIGH_STOCK)}")
        print(f"  len(LOW) = {len(PRODUCTS_LOW_STOCK)}")
        if self.headers is None:
            print("❌ Auth problem: self.headers is None")
            return
    
        if not PRODUCTS_HIGH_STOCK:
            print(f"❌ Products problem: PRODUCTS_HIGH_STOCK = {PRODUCTS_HIGH_STOCK}")
            return
        product_id = random.choice(PRODUCTS_HIGH_STOCK)
        quantity  = random.randint(1, 3)

        self.client.delete(f"{CART_HOST}/api/cart", headers=self.headers, name="Cart: Clear")
        #add to cart
        cart_resp = self.client.post(
            f"{CART_HOST}/api/cart",
            json={"productId": product_id, "quantity": quantity},
            headers=self.headers, name="Cart: Add (High)"
        )
        if cart_resp.status_code not in (200, 201):
            saga_stats['errors'] += 1
            return

        with self.client.post(
            f"{ORDER_COMMAND_HOST}/api/orders",
            json={"buyerName": f"User_{random.randint(1000, 9999)}",
                  "buyerPhone": "1234567890", "buyerAddress": "Test Address"},
            headers=self.headers, catch_response=True, name="Order (High Stock)"
        ) as resp:
            if resp.status_code in (200, 201):
                oid = resp.json().get('orderId')
                if oid:
                    order_journal[oid] = {'type': 'high', 'http': 200}
                    saga_stats['total_orders'] += 1
                    saga_stats['high_stock_orders'] += 1
                    resp.success()
                else:
                    resp.failure("No orderId")
                    saga_stats['errors'] += 1
            else:
                # 500 也记
                oid = f"err_{uuid.uuid4().hex}"
                order_journal[oid] = {'type': 'high', 'http': resp.status_code}
                saga_stats['errors'] += 1
                resp.failure(f"http_{resp.status_code}")

    # --- 低库存任务 ---
    @task(50)
    def test_low_stock_order(self):
        print("LOW STOCK user test started")
        print(f"\n🔍 DEBUG in task:")
        print(f"  PRODUCTS_HIGH_STOCK = {PRODUCTS_HIGH_STOCK}")
        print(f"  PRODUCTS_LOW_STOCK = {PRODUCTS_LOW_STOCK}")
        print(f"  len(HIGH) = {len(PRODUCTS_HIGH_STOCK)}")
        print(f"  len(LOW) = {len(PRODUCTS_LOW_STOCK)}")
        if self.headers is None:
            print("❌ Auth problem: self.headers is None")
            return
    
        if not PRODUCTS_LOW_STOCK:
            print(f"❌ Products problem: PRODUCTS_LOW_STOCK = {PRODUCTS_LOW_STOCK}")
            return
        product_id = random.choice(PRODUCTS_LOW_STOCK)
        quantity  = 9999

        self.client.delete(f"{CART_HOST}/api/cart", headers=self.headers, name="Cart: Clear")
        cart_resp = self.client.post(
            f"{CART_HOST}/api/cart",
            json={"productId": product_id, "quantity": quantity},
            headers=self.headers, name="Cart: Add (Low)"
        )
        if cart_resp.status_code not in (200, 201):
            saga_stats['errors'] += 1
            return

        with self.client.post(
            f"{ORDER_COMMAND_HOST}/api/orders",
            json={"buyerName": f"User_{random.randint(1000, 9999)}",
                  "buyerPhone": "1234567890", "buyerAddress": "Test Address"},
            headers=self.headers, catch_response=True, name="Order (Low Stock)"
        ) as resp:
            if resp.status_code in (200, 201):
                oid = resp.json().get('orderId')
                if oid:
                    order_journal[oid] = {'type': 'low', 'http': 200}
                    saga_stats['total_orders'] += 1
                    saga_stats['low_stock_orders'] += 1
                    resp.success()
                else:
                    resp.failure("No orderId")
                    saga_stats['errors'] += 1
            else:
                oid = f"err_{uuid.uuid4().hex}"
                order_journal[oid] = {'type': 'low', 'http': resp.status_code}
                saga_stats['errors'] += 1
                resp.failure(f"http_{resp.status_code}")

# ---------- 3. 精准对账 ----------
@events.test_stop.add_listener
def on_test_stop(environment, **kwargs):
    print("\n" + "="*70)
    print("🎯 精准 SAGA 对账")
    print("="*70)
    print(f"\n⏰ Waiting 100 seconds for Saga to complete...")
    time.sleep(100)

    # 拿任意账号 token
    r = requests.post(f"{AUTH_HOST}/api/auth/login",
                      json={"email": "test1@example.com", "password": "password123"}, timeout=5)
    if r.status_code != 200:
        print("❌ 无法登录，对账终止")
        return
    headers = {"Authorization": f"Bearer {r.json()['data']['accessToken']}"}

    # 反查每个 orderId
    for oid, rec in order_journal.items():
        if oid.startswith("err_"):
            rec['final'] = 'HTTP_500_NEVER_CREATED'
            continue
        db_resp = requests.get(f"{ORDER_QUERY_HOST}/api/orders/{oid}", headers=headers)
        rec['final'] = db_resp.json().get('status') if db_resp.status_code == 200 else 'NOT_FOUND'

    # 零误差统计
    high = [o for o in order_journal.values() if o['type'] == 'high']
    low  = [o for o in order_journal.values() if o['type'] == 'low']

    def count(sub, key, val):
        return len([o for o in sub if o.get(key) == val])

    print(f"\n高库存 - 提交: {len(high)}")
    print(f"  ├─ CONFIRMED : {count(high, 'final', 'CONFIRMED')}")
    print(f"  ├─ CANCELLED : {count(high, 'final', 'CANCELLED')}")
    print(f"  └─ HTTP 500  : {count(high, 'http', 500)}")

    print(f"\n低库存 - 提交: {len(low)}")
    print(f"  ├─ CONFIRMED : {count(low, 'final', 'CONFIRMED')}")
    print(f"  ├─ CANCELLED : {count(low, 'final', 'CANCELLED')}")
    print(f"  └─ HTTP 500  : {count(low, 'http', 500)}")

    print("="*70)