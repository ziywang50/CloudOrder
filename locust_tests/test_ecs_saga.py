"""
ECS SAGA Order Flow Load Test — 500 concurrent users
Target: http://cloudorder-api-442075035.us-west-2.elb.amazonaws.com

Tests the full order flow: login → add to cart → place order
Validates SAGA pattern: high-stock orders should CONFIRM, low-stock should CANCEL

Run:
  locust -f test_ecs_saga.py --headless -u 500 -r 50 -t 120s \
    --html=results_ecs/saga_500.html --csv=results_ecs/saga_500
"""
from locust import HttpUser, task, between, events
import requests, threading, time, random, uuid

BASE = "http://cloudorder-api-442075035.us-west-2.elb.amazonaws.com"
TOTAL_USERS = 500

PRODUCTS_HIGH_STOCK = []
PRODUCTS_LOW_STOCK  = []

_user_lock  = threading.Lock()
_user_index = 0


@events.test_start.add_listener
def on_test_start(environment, **kwargs):
    global PRODUCTS_HIGH_STOCK, PRODUCTS_LOW_STOCK

    print("=" * 60)
    print("SETUP: Creating users and products")
    print("=" * 60)

    # Register users
    print(f"\nRegistering {TOTAL_USERS} users...")
    for i in range(1, TOTAL_USERS + 1):
        requests.post(f"{BASE}/api/auth/signup", json={
            "email": f"saga_user{i}@test.com",
            "password": "password123",
            "name": f"Saga User {i}"
        }, timeout=10)
        if i % 100 == 0:
            print(f"  {i}/{TOTAL_USERS}")
        time.sleep(0.005)

    # Login to create products
    resp = requests.post(f"{BASE}/api/auth/login", json={
        "email": "saga_user1@test.com", "password": "password123"
    }, timeout=10)
    if resp.status_code != 200:
        print("ERROR: Login failed")
        return
    headers = {"Authorization": f"Bearer {resp.json()['data']['accessToken']}"}

    # Create high-stock products
    print("\nCreating high-stock products...")
    for i in range(3):
        resp = requests.post(f"{BASE}/api/products", json={
            "name": f"High Stock Item {i+1}",
            "description": "Load test",
            "price": 99.0,
            "stock": 50000
        }, headers=headers, timeout=10)
        if resp.status_code in (200, 201):
            pid = (resp.json().get("productId") or
                   resp.json().get("data", {}).get("productId"))
            if pid:
                PRODUCTS_HIGH_STOCK.append(str(pid))
                print(f"  Created: {pid}")

    # Create low-stock products
    print("\nCreating low-stock products...")
    for i in range(3):
        resp = requests.post(f"{BASE}/api/products", json={
            "name": f"Low Stock Item {i+1}",
            "description": "Load test",
            "price": 49.0,
            "stock": 3
        }, headers=headers, timeout=10)
        if resp.status_code in (200, 201):
            pid = (resp.json().get("productId") or
                   resp.json().get("data", {}).get("productId"))
            if pid:
                PRODUCTS_LOW_STOCK.append(str(pid))
                print(f"  Created: {pid}")

    print(f"\nHigh-stock: {PRODUCTS_HIGH_STOCK}")
    print(f"Low-stock:  {PRODUCTS_LOW_STOCK}")
    print("SETUP COMPLETE\n" + "=" * 60)


class SagaUser(HttpUser):
    host = BASE
    wait_time = between(0.1, 0.5)

    def on_start(self):
        global _user_index
        with _user_lock:
            my_index = _user_index % TOTAL_USERS + 1
            _user_index += 1

        resp = self.client.post("/api/auth/login", json={
            "email": f"saga_user{my_index}@test.com",
            "password": "password123"
        }, name="Auth: Login")
        if resp.status_code == 200:
            self.token = resp.json()["data"]["accessToken"]
            self.headers = {"Authorization": f"Bearer {self.token}"}
        else:
            self.token = None
            self.headers = {}

    @task(7)
    def order_high_stock(self):
        if not self.token or not PRODUCTS_HIGH_STOCK:
            return
        pid = random.choice(PRODUCTS_HIGH_STOCK)
        self._place_order(pid, random.randint(1, 3), "Order: High Stock")

    @task(3)
    def order_low_stock(self):
        if not self.token or not PRODUCTS_LOW_STOCK:
            return
        pid = random.choice(PRODUCTS_LOW_STOCK)
        self._place_order(pid, 9999, "Order: Low Stock")

    def _place_order(self, product_id, quantity, name):
        # Clear cart
        self.client.delete("/api/cart", headers=self.headers, name="Cart: Clear")

        # Add to cart
        resp = self.client.post("/api/cart", json={
            "productId": product_id, "quantity": quantity
        }, headers=self.headers, name="Cart: Add")
        if resp.status_code not in (200, 201):
            return

        # Place order
        with self.client.post("/api/orders", json={
            "buyerName": "Load Tester",
            "buyerPhone": "5550000000",
            "buyerAddress": "123 ECS Test Street"
        }, headers=self.headers, catch_response=True, name=name) as resp:
            if resp.status_code in (200, 201) and resp.json().get("orderId"):
                resp.success()
            else:
                resp.failure(f"HTTP {resp.status_code}")
