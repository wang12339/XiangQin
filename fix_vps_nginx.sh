#!/bin/bash
ssh root@43.99.13.12 'sed -i "/proxy_pass http:\/\/127.0.0.1:8766;/a\\        client_body_buffer_size 10m;" /etc/nginx/conf.d/xiangqin-relay.conf && nginx -t && systemctl reload nginx'
