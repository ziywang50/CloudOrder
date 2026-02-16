locust -f test_writedb_multiuser.py --headless -u 50 -r 10 -t 60s \
  --html=report_write_multiuser.html \
  --csv=results_write_multiuser

locust -f test_readdb_multiuser.py --headless -u 50 -r 10 -t 60s \
  --html=report_read_multiuser.html \
  --csv=results_read_multiuser

