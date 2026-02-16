import requests

# 配置
AUTH_HOST = "http://localhost:8088"
ORDER_HOST = "http://localhost:8090"

# 登录获取 token
def login(email):
    response = requests.post(f"{AUTH_HOST}/api/auth/login", json={
        "email": email,
        "password": "password123"
    })
    if response.status_code != 200 and response.status_code != 201:
        print(f"❌ Login failed for {email}")
        return None
    return response.json()['data']['accessToken']

# 获取用户订单
def get_user_orders(user_id, token):
    headers = {"Authorization": f"Bearer {token}"}
    response = requests.get(f"{ORDER_HOST}/api/orders/writedb/user/{user_id}", headers=headers)
    if response.status_code != 200:
        return []
    return response.json()

# 主逻辑
def main():
    total_confirmed = 0
    total_cancelled = 0
    total_pending = 0
    total_orders = 0

    for i in range(1, 51):
        email = f"test{i}@example.com"
        token = login(email)
        if not token:
            continue

        orders = get_user_orders(i, token)
        confirmed = len([o for o in orders if o.get('status') == 'CONFIRMED'])
        cancelled = len([o for o in orders if o.get('status') == 'CANCELLED'])
        pending = len([o for o in orders if o.get('status') == 'PENDING'])

        total_confirmed += confirmed
        total_cancelled += cancelled
        total_pending += pending
        total_orders += len(orders)

        print(f"test{i} | CONFIRMED: {confirmed} | CANCELLED: {cancelled} | PENDING: {pending}")

    print("\n" + "="*50)
    print("📊 所有用户订单汇总：")
    print(f"  总订单数：{total_orders}")
    print(f"  CONFIRMED: {total_confirmed}")
    print(f"  CANCELLED: {total_cancelled}")
    print(f"  PENDING: {total_pending}")
    print("="*50)

if __name__ == "__main__":
    main()