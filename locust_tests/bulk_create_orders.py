# bulk_create_orders_debug.py
# 带详细错误日志的版本
import requests
import concurrent.futures
import time
import random
from collections import defaultdict

BASE_URL = "http://localhost"
PRODUCTS = [1771003206861, 1771003226827, 1771003239882, 1771003253065]

# 全局错误统计
error_details = defaultdict(list)


def create_orders_for_single_user(user_num, num_orders):
    """为一个用户顺序创建所有订单，记录详细错误"""
    email = f"test{user_num}@example.com"
    
    # Login
    try:
        login_resp = requests.post(
            f"{BASE_URL}:8088/api/auth/login",
            json={"email": email, "password": "password123"},
            timeout=10
        )
        
        if login_resp.status_code not in [200, 201]:
            error_details["LOGIN_FAILED"].append({
                "user": user_num,
                "status": login_resp.status_code,
                "body": login_resp.text[:200]
            })
            return (user_num, 0, [], "LOGIN_FAILED")
        
        token = login_resp.json()['data']['accessToken']
        headers = {"Authorization": f"Bearer {token}"}
        
    except requests.Timeout:
        error_details["LOGIN_TIMEOUT"].append({"user": user_num})
        return (user_num, 0, [], "LOGIN_TIMEOUT")
    except Exception as e:
        error_details["LOGIN_ERROR"].append({"user": user_num, "error": str(e)})
        return (user_num, 0, [], f"LOGIN_ERROR: {e}")
    
    success_count = 0
    user_errors = []
    
    for order_idx in range(num_orders):
        error_info = {"user": user_num, "order_idx": order_idx}
        
        try:
            # Step 1: Clear cart
            try:
                clear_resp = requests.delete(
                    f"{BASE_URL}:8083/api/cart", 
                    headers=headers, 
                    timeout=5
                )
                if clear_resp.status_code >= 500:
                    error_info["step"] = "CLEAR_CART"
                    error_info["status"] = clear_resp.status_code
                    error_info["body"] = clear_resp.text[:100]
                    error_details["CLEAR_CART_5XX"].append(error_info.copy())
                    user_errors.append(error_info.copy())
                    continue
            except requests.Timeout:
                error_details["CLEAR_CART_TIMEOUT"].append(error_info.copy())
                user_errors.append({"step": "CLEAR_CART_TIMEOUT", **error_info})
                continue
            
            # Step 2: Add items to cart
            num_items = random.randint(2, 4)
            add_failed = False
            
            for i in range(num_items):
                product_id = PRODUCTS[(order_idx + i) % len(PRODUCTS)]
                
                try:
                    add_resp = requests.post(
                        f"{BASE_URL}:8083/api/cart",
                        json={"productId": product_id, "quantity": random.randint(1, 2)},
                        headers=headers,
                        timeout=5
                    )
                    
                    if add_resp.status_code not in [200, 201, 204]:
                        error_info["step"] = "ADD_CART"
                        error_info["status"] = add_resp.status_code
                        error_info["body"] = add_resp.text[:100]
                        error_info["product_id"] = product_id
                        error_details[f"ADD_CART_{add_resp.status_code}"].append(error_info.copy())
                        user_errors.append(error_info.copy())
                        add_failed = True
                        break
                        
                except requests.Timeout:
                    error_details["ADD_CART_TIMEOUT"].append(error_info.copy())
                    user_errors.append({"step": "ADD_CART_TIMEOUT", **error_info})
                    add_failed = True
                    break
            
            if add_failed:
                continue
            
            # Step 3: Create order
            try:
                order_resp = requests.post(
                    f"{BASE_URL}:8090/api/orders",
                    json={
                        "buyerName": f"User{user_num}",
                        "buyerPhone": "1234567890",
                        "buyerAddress": "Test Address"
                    },
                    headers=headers,
                    timeout=15
                )
                
                if order_resp.status_code in [200, 201]:
                    success_count += 1
                else:
                    error_info["step"] = "CREATE_ORDER"
                    error_info["status"] = order_resp.status_code
                    error_info["body"] = order_resp.text[:200]
                    error_details[f"CREATE_ORDER_{order_resp.status_code}"].append(error_info.copy())
                    user_errors.append(error_info.copy())
                    
            except requests.Timeout:
                error_details["CREATE_ORDER_TIMEOUT"].append(error_info.copy())
                user_errors.append({"step": "CREATE_ORDER_TIMEOUT", **error_info})
                
        except requests.ConnectionError as e:
            error_info["step"] = "CONNECTION_ERROR"
            error_info["error"] = str(e)[:100]
            error_details["CONNECTION_ERROR"].append(error_info.copy())
            user_errors.append(error_info.copy())
            
        except Exception as e:
            error_info["step"] = "UNKNOWN"
            error_info["error"] = str(e)[:100]
            error_details["UNKNOWN_ERROR"].append(error_info.copy())
            user_errors.append(error_info.copy())
        
        # Small delay
        time.sleep(0.05)
    
    return (user_num, success_count, user_errors, "DONE")


def bulk_create(orders_per_user, num_users=50, workers=10):
    """批量创建，带详细错误报告"""
    
    total = orders_per_user * num_users
    
    print(f"\n{'='*70}")
    print(f"🚀 BULK CREATION (DEBUG MODE)")
    print(f"{'='*70}")
    print(f"  Users: {num_users}, Orders/user: {orders_per_user}")
    print(f"  Total: {total}, Workers: {workers}")
    print(f"{'='*70}\n")
    
    start = time.time()
    total_success = 0
    all_errors = []
    
    with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as executor:
        futures = [
            executor.submit(create_orders_for_single_user, user_num, orders_per_user)
            for user_num in range(1, num_users + 1)
        ]
        
        for i, future in enumerate(concurrent.futures.as_completed(futures), 1):
            user_num, success, user_errors, status = future.result()
            total_success += success
            all_errors.extend(user_errors)
            
            pct = i / num_users * 100
            failed = orders_per_user - success
            
            # 显示失败原因
            if failed > 0 and user_errors:
                error_types = defaultdict(int)
                for e in user_errors:
                    error_types[e.get("step", "UNKNOWN")] += 1
                error_summary = ", ".join([f"{k}:{v}" for k, v in error_types.items()])
                print(f"[{i:>2}/{num_users}] User {user_num:>2}: "
                      f"✅{success} ❌{failed} | {error_summary}")
            else:
                print(f"[{i:>2}/{num_users}] User {user_num:>2}: "
                      f"✅{success}/{orders_per_user} | {pct:.0f}%")
    
    elapsed = time.time() - start
    failed_total = total - total_success
    
    # 详细错误报告
    print(f"\n{'='*70}")
    print(f"📊 SUMMARY")
    print(f"{'='*70}")
    print(f"  Success: {total_success}/{total} ({total_success/total*100:.1f}%)")
    print(f"  Failed: {failed_total}")
    print(f"  Time: {elapsed/60:.1f} minutes")
    
    if error_details:
        print(f"\n{'='*70}")
        print(f"❌ ERROR BREAKDOWN")
        print(f"{'='*70}")
        for error_type, errors in sorted(error_details.items(), key=lambda x: -len(x[1])):
            print(f"\n  {error_type}: {len(errors)} occurrences")
            # 显示前3个示例
            for sample in errors[:3]:
                print(f"    → {sample}")
    
    print(f"\n{'='*70}")
    
    # 保存完整错误日志
    import json
    with open("error_log.json", "w") as f:
        json.dump({
            "summary": {
                "total": total,
                "success": total_success,
                "failed": failed_total,
                "time_minutes": elapsed/60
            },
            "error_counts": {k: len(v) for k, v in error_details.items()},
            "error_details": {k: v[:10] for k, v in error_details.items()}  # 每种只存10个
        }, f, indent=2)
    
    print(f"📁 Detailed errors saved to: error_log.json")
    print(f"{'='*70}\n")


if __name__ == "__main__":
    import sys
    orders_per_user = int(sys.argv[1]) if len(sys.argv) > 1 else 30
    bulk_create(orders_per_user, workers=10)