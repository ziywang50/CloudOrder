"""
ECS Seckill Load Test — 5000 concurrent users
Target: http://cloudorder-api-442075035.us-west-2.elb.amazonaws.com

Pre-requisites (run setup_ecs_seckill.py first):
  - Seckill activity created in Redis with large stock
  - 5000 test user accounts created

Run:
  locust -f test_ecs_seckill.py --headless -u 5000 -r 500 -t 120s \
    --html=results_ecs/seckill_5000.html --csv=results_ecs/seckill_5000
"""
from locust import FastHttpUser, task, between, events
import requests, threading, time, random
from concurrent.futures import ThreadPoolExecutor, as_completed

BASE = "http://cloudorder-api-1296270470.us-west-2.elb.amazonaws.com"
TOTAL_USERS = 5000

# Set to True to skip user registration (users already exist from a previous run)
SKIP_SETUP = False

# Manually configured seckill products — randomly distributed across users
# 1776139287053: extreme condition (Redis stock=100001, actual DB stock=100000)
#SECKILL_PRODUCTS = ["1776137086811", "1776139287053", "1776137138437"]
SECKILL_PRODUCTS = ["1777261394256", "1777261408910", "1777261424082"]

# Thread-safe user index assignment
_user_lock = threading.Lock()
_user_index = 0


def _register_user(i):
    try:
        requests.post(f"{BASE}/api/auth/signup", json={
            "email": f"seckill_user{i}@test.com",
            "password": "password123",
            "username": f"Seckill User {i}"
        }, timeout=10)
    except Exception:
        pass


@events.test_start.add_listener
def on_test_start(environment, **kwargs):
    if SKIP_SETUP:
        print(f"Skipping setup — using existing users and products: {SECKILL_PRODUCTS}")
        return

    print("=" * 60)
    print(f"SETUP: Registering {TOTAL_USERS} users in parallel (50 workers)")
    print("=" * 60)

    completed = 0
    with ThreadPoolExecutor(max_workers=50) as executor:
        futures = {executor.submit(_register_user, i): i for i in range(1, TOTAL_USERS + 1)}
        for future in as_completed(futures):
            completed += 1
            if completed % 500 == 0:
                print(f"  {completed}/{TOTAL_USERS} done")

    print(f"\nUsing seckill products: {SECKILL_PRODUCTS}")
    print("SETUP COMPLETE — starting load test\n" + "=" * 60)


class SeckillUser(FastHttpUser):
    host = BASE
    wait_time = between(0.1, 0.5)   # tight wait for max QPS

    def on_start(self):
        global _user_index
        with _user_lock:
            my_index = _user_index % TOTAL_USERS + 1
            _user_index += 1

        self.email = f"seckill_user{my_index}@test.com"
        resp = self.client.post("/api/auth/login", json={
            "email": self.email,
            "password": "password123"
        }, name="Auth: Login")
        if resp.status_code == 200:
            self.token = resp.json()["data"]["accessToken"]
            self.headers = {"Authorization": f"Bearer {self.token}", "Content-Type": "application/json"}
        else:
            self.token = None
            self.headers = {}

    @task
    def seckill(self):
        if not self.token:
            return
        product_id = random.choice(SECKILL_PRODUCTS)
        with self.client.post(
            "/api/seckill/seckill",
            json={"productId": product_id, "quantity": 1},
            headers=self.headers,
            catch_response=True,
            name="Seckill: Purchase"
        ) as resp:
            if resp.status_code == 200:
                body = resp.json()
                if body.get("success"):
                    resp.success()
                    reservation_id = body.get("reservationId")
                    if reservation_id:
                        time.sleep(0.01)
                        self.client.post("/api/seckill/confirm", json={
                            "reservationId": reservation_id,
                            "address": "123 Test St",
                            "paymentMethod": "CREDIT_CARD",
                            "buyerName": "Load Tester",
                            "buyerPhone": "5550000000"
                        }, headers=self.headers, name="Seckill: Confirm")
                else:
                    # Sold out / already purchased — not a failure
                    resp.success()
            else:
                # Log status + first 200 chars of body to identify root cause
                body_preview = resp.text[:200] if resp.text else "(empty)"
                resp.failure(f"HTTP {resp.status_code}: {body_preview}")
