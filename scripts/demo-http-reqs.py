#!/usr/bin/env bash
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import asyncio
import gzip

HOST = '127.0.0.1'
PORT = 57025

async def print_async_read(reader):
  try:
    async with asyncio.timeout(0.001):
      data = await reader.read(1024)
      print('Received', data)
  except TimeoutError:
    pass

async def http_pipelining(client_id, sleep_sec=3):
  reader, writer = await asyncio.open_connection(HOST, PORT)
  try:
    chunks = [
        b'GET /demo/standard?ms=2500 HTTP/1.0\r\n',
        b'Connection: keep-alive\r\n',
        b'\r\n',
        b'GET /demo/standard?ms=2500 HTTP/1.0\r\n',
        b'Connection: keep-alive\r\n',
        b'\r\n',
        b'GET /demo/standard?ms=2500 HTTP/1.0\r\n',
        b'Connection: close\r\n',
        b'\r\n',
    ]

    for chunk in chunks:
        await asyncio.sleep(sleep_sec)
        print('Send:', chunk)
        writer.write(chunk)
        await writer.drain()

    while True:
        data = await reader.read(1024)
        if not data: break
        print('Received', client_id, 'data', data.decode())
  except ConnectionResetError:
    print('---> Connection Reset', client_id, '<---')
  except BrokenPipeError:
    print('---> Broken Pipe', client_id, '<---')
  finally:
    try:
      print('Close the connection', client_id)
      writer.close()
      await writer.wait_closed()
    except:
      pass

async def http_slow_client(client_id, body_size, sleep_sec=3):
    reader, writer = await asyncio.open_connection(HOST, PORT)
    try:
      print('Connected', client_id)

      chunks = [
        b'POST /foo HTTP/1.0\r\n',
        #b'content-length: %d\r\n' % body_size,
        b'Transfer-Encoding: chunked\r\n',
        b'\r\n'
      ]

      for chunk in chunks:
        await asyncio.sleep(sleep_sec)
        print('Send:', chunk)
        writer.write(chunk)
        await writer.drain()

      await print_async_read(reader)

      body_remaining = body_size
      while body_remaining > 0:
        size = min(body_remaining, 1024)
        body_remaining -= size
        chunk = b'x' * size
        await asyncio.sleep(sleep_sec)
        print('Send:', chunk)
        await print_async_read(reader)
        if True:
          # transfer-encoding: chunked
          writer.write(b'%x\r\n' % len(chunk))
          writer.write(chunk)
          writer.write(b'\r\n')
          if body_remaining == 0:
            writer.write(b'\r\n')
        else:
          # content-length: 123
          writer.write(chunk)
        await writer.drain()

      data = await reader.read(1024)
      print('Received', client_id, 'data', data.decode())
    except ConnectionResetError:
      print('---> Connection Reset', client_id, '<---')
    except BrokenPipeError:
      print('---> Broken Pipe', client_id, '<---')
    finally:
      try:
        print('Close the connection', client_id)
        writer.close()
        await writer.wait_closed()
      except:
        pass

async def http_lots_of_slow_clients(body_size):
  async with asyncio.TaskGroup() as tg:
    for i in range(100):
      tg.create_task(http_slow_client(i, body_size, 0.01))
  print('lots of slow clients done')


async def noop_client(client_id):
    reader, writer = await asyncio.open_connection(HOST, PORT)
    try:
      await asyncio.sleep(60)
    finally:
      try:
        print('Close the connection', client_id)
        writer.close()
        await writer.wait_closed()
      except:
        pass

async def noop_lots_clients():
  async with asyncio.TaskGroup() as tg:
    for i in range(256 + 32):
      tg.create_task(noop_client(i))
  print('lots of slow clients done')

#asyncio.run(noop_lots_clients())

#asyncio.run(http_slow_client(0, 128))
#asyncio.run(http_lots_of_slow_clients(4 << 20))
asyncio.run(http_pipelining(1, 0))

#asyncio.run(http_slow_client(0, 260, 0))