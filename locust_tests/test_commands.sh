#Write db direct get
locust -f test_write_db.py --headless -u 50 -r 10 -t 60s --html=report_write_db.html --csv=results_write_db
#Get from read db with cache
# 1 Cache warmup）
curl -H "Authorization: Bearer YOUR_TOKEN" \
  http://localhost:8091/api/orders/user/2

# 2. Run test
locust -f test_read_db_with_cache.py --headless -u 50 -r 10 -t 60s \
  --html=report_read_db_cached.html \
  --csv=results_read_db_cached

#3
$ locust -f test_read_db_no_cache.py --headless -u 50 -r 10 -t 60s \
  --html=report_read_db_no_cache.html \
  --csv=results_read_db_no_cache

#Experiment2: test saga under load
$ locust -f test_saga_load.py --headless -u 50 -r 10 -t 60s \
  --html=report_saga_load.html \
  --csv=results_saga_load

#Experiment3
$ locust -f exp_3_test_read.py --headless -u 50 -r 10 -t 60s \
  --html=report_product_read.html \
  --csv=results_product_read

$ locust -f exp_3_test_write.py --headless -u 50 -r 10 -t 60s \
  --html=report_product_write.html \
  --csv=results_product_write