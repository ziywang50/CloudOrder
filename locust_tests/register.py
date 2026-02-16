# create_test_users.py
import requests
import time

AUTH_URL = "http://localhost:8088/api/auth"

print("🚀 Creating 50 test users...")
print("="*60)

success_count = 0
failed_count = 0
existing_count = 0

for i in range(1, 51):
    user_data = {
        "email": f"test{i}@example.com",
        "password": "password123",
        "name": f"Test User {i}"
    }
    
    try:
        response = requests.post(
            f"{AUTH_URL}/signup", 
            json=user_data,
            timeout=5
        )
        
        if response.status_code in [200, 201]:
            print(f"✅ [{i}/50] Created: test{i}@example.com")
            success_count += 1
        elif response.status_code == 409 or response.status_code == 400:
            # 用户已存在
            print(f"⚠️  [{i}/50] Already exists: test{i}@example.com")
            existing_count += 1
        else:
            print(f"❌ [{i}/50] Failed: test{i}@example.com - Status {response.status_code}")
            print(f"   Response: {response.text[:100]}")
            failed_count += 1
            
    except requests.exceptions.ConnectionError:
        print(f"❌ [{i}/50] Connection failed - Is AuthService running?")
        failed_count += 1
        break
    except Exception as e:
        print(f"❌ [{i}/50] Error: {e}")
        failed_count += 1
    
    # 小延迟避免压垮服务
    time.sleep(0.1)

print("\n" + "="*60)
print("📊 Registration Summary:")
print(f"  ✅ Newly created: {success_count}")
print(f"  ⚠️  Already existed: {existing_count}")
print(f"  ❌ Failed: {failed_count}")
print(f"  📝 Total available: {success_count + existing_count}/50")

if success_count + existing_count >= 50:
    print("\n✅ All 50 test users are ready!")
    print("🚀 You can now run: locust -f test_saga_corrected.py --headless -u 50 -r 10 -t 60s")
else:
    print(f"\n⚠️  Only {success_count + existing_count} users available")
    print("   Check if AuthService is running correctly")

print("="*60)