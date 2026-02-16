# test_write_db_multiuser.py
from locust import HttpUser, task, between
import threading

class WriteDBMultiUser(HttpUser):
    wait_time = between(0.5, 1)
    host = "http://localhost:8090"
    
    user_counter = 0
    counter_lock = threading.Lock()
    
    def on_start(self):
        # Assign user number (1-50) round-robin
        with self.counter_lock:
            user_num = (WriteDBMultiUser.user_counter % 50) + 1
            WriteDBMultiUser.user_counter += 1
        
        email = f"test{user_num}@example.com"
        
        # Login
        response = self.client.post(
            "http://localhost:8088/api/auth/login",
            json={"email": email, "password": "password123"}
        )
        
        if response.status_code == 200:
            self.token = response.json()['data']['accessToken']
            self.headers = {"Authorization": f"Bearer {self.token}"}
            self.user_id = user_num  # ID matches number!
        else:
            print(f"❌ Login failed: {email}")
            self.environment.runner.quit()
    
    @task
    def query_orders(self):
        """Each user queries their own orders from Write DB"""
        with self.client.get(
            f"/api/orders/writedb/user/{self.user_id}",
            headers=self.headers,
            catch_response=True,
            name="Write-DB: Query Own Orders"
        ) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"User {self.user_id}: {response.status_code}")