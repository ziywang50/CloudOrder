from locust import HttpUser, task, between

class ReadDBWithCacheUser(HttpUser):
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
        # 重复查询同一用户，测试缓存命中
        with self.client.get(
            "/api/orders/user/2",
            headers=self.headers,
            catch_response=True,
            name="Read-DB-WithCache: Query Orders"
        ) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Failed: {response.status_code}")