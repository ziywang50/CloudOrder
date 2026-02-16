# test_product_read_comparison.py
from locust import HttpUser, task, between
import random

class ProductReadComparisonUser(HttpUser):
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
    
    @task(3)
    def read_from_dynamodb(self):
        """DynamoDB点查询"""
        product_id = random.choice(self.product_ids)
        
        with self.client.get(
            f"/api/products/{product_id}",
            headers=self.headers,
            catch_response=True,
            name="DynamoDB: Get by ID"
        ) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Failed: {response.status_code}")
    
    @task(3)
    def read_from_elasticsearch(self):
        """Elasticsearch点查询（需要先添加endpoint）"""
        product_id = random.choice(self.product_ids)
        
        with self.client.get(
            f"/api/products/query/{product_id}",  # 新endpoint
            headers=self.headers,
            catch_response=True,
            name="Elasticsearch: Get by ID"
        ) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Failed: {response.status_code}")
    
    @task(4)
    def search_in_elasticsearch(self):
        """Elasticsearch搜索"""
        keywords = ["test", "product", "item", "sample"]
        keyword = random.choice(keywords)
        
        with self.client.get(
            f"/api/products/search?keyword={keyword}",
            headers=self.headers,
            catch_response=True,
            name="Elasticsearch: Search"
        ) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Failed: {response.status_code}")