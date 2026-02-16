from locust import HttpUser, task, between
import random

class ReadDBNoCacheUser(HttpUser):
    wait_time = between(0.5, 1)
    host = "http://localhost:8091"  # OrderQueryService
    
    def on_start(self):
        response = self.client.post(
            "http://localhost:8088/api/auth/login",
            json={"email": "test@example.com", "password": "password123"}
        )
        self.token = response.json()['data']['accessToken']
        self.headers = {"Authorization": f"Bearer {self.token}"}
    
    @task
    def query_orders(self):
        with self.client.get(
            f"/api/orders/user/2",
            headers=self.headers,
            catch_response=True,
            name="Read-DB-NoCache: Query Orders"
        ) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Failed: {response.status_code}")