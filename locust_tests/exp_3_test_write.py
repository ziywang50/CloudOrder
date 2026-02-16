# test_product_write.py
from locust import HttpUser, task, between
import random

class ProductWriteUser(HttpUser):
    wait_time = between(0.5, 1)
    host = "http://localhost:8089"
    
    product_ids = [
        "1765411806455",
        "1765403002318", 
        "1765403002318",
        "1765403002318",
        "1765421983848"
    ]
    
    def on_start(self):
        response = self.client.post(
            "http://localhost:8088/api/auth/login",
            json={"email": "test@example.com", "password": "password123"}
        )
        self.token = response.json()['data']['accessToken']
        self.headers = {"Authorization": f"Bearer {self.token}"}
    
    @task
    def deduct_stock(self):
        """扣库存（DynamoDB写）"""
        product_id = random.choice(self.product_ids)
        
        with self.client.put(
            f"/api/products/{product_id}/deduct-stock",
            json={"quantity": 1},
            headers=self.headers,
            catch_response=True,
            name="DynamoDB: Deduct Stock"
        ) as response:
            if response.status_code in [200, 201, 400, 409]:
                # 400/409是库存不足，算正常
                response.success()
            else:
                response.failure(f"Failed: {response.status_code}")