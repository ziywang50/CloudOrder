from locust import HttpUser, task, between
import random

class WriteDBUser(HttpUser):
    wait_time = between(0.5, 1)
    host = "http://localhost:8090"
    
    # 假设这些是你的产品ID
    product_ids = [1765411806380, 1765411806455, 1765403002318, 1765411805958, 1765411806535, 1765411806617, 1765402943426]
    
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
            "/api/orders/writedb/user/2",
            headers=self.headers,
            catch_response=True,
            name="Write-DB: Query Orders"
        ) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Failed: {response.status_code}")
    
    # @task(1)  # 权重1：偶尔创建订单
    # def create_order(self):
    #     # 先添加到购物车
    #     product_id = random.choice(self.product_ids)
    #     cart_response = self.client.post(
    #         "http://localhost:8083/api/cart",
    #         headers=self.headers,
    #         json={"productId": product_id, "quantity": 1},
    #         name="Add to Cart"
    #     )
        
    #     if cart_response.status_code != 200:
    #         return
        
    #     # 创建订单
    #     with self.client.post(
    #         "/api/orders",
    #         headers=self.headers,
    #         json={
    #             "buyerName": "Locust User",
    #             "buyerPhone": "1234567890",
    #             "buyerAddress": "Test Address"
    #         },
    #         catch_response=True,
    #         name="Write-DB: Create Order"
    #     ) as response:
    #         if response.status_code == 200:
    #             response.success()
    #         else:
    #             response.failure(f"Failed: {response.status_code}")