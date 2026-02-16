# test_read_db_cached_multiuser.py
from locust import HttpUser, task, between
import threading

class ReadDBCachedMultiUser(HttpUser):
    wait_time = between(0.5, 1)
    host = "http://localhost:8091"
    
    user_counter = 0
    counter_lock = threading.Lock()
    
    def on_start(self):
        with self.counter_lock:
            user_num = (ReadDBCachedMultiUser.user_counter % 50) + 1
            ReadDBCachedMultiUser.user_counter += 1
        
        email = f"test{user_num}@example.com"
        
        response = self.client.post(
            "http://localhost:8088/api/auth/login",
            json={"email": email, "password": "password123"}
        )
        
        if response.status_code == 200:
            self.token = response.json()['data']['accessToken']
            self.headers = {"Authorization": f"Bearer {self.token}"}
            self.user_id = user_num
        else:
            self.environment.runner.quit()
    
    @task
    def query_orders(self):
        """Each user queries their own orders (with cache)"""
        with self.client.get(
            f"/api/orders/user/{self.user_id}",
            headers=self.headers,
            catch_response=True,
            name="Read-DB-Cached: Query Own Orders"
        ) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"User {self.user_id}: {response.status_code}")