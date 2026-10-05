// Explicit local development fixture. Never enabled by the application itself.
import { createServer } from 'node:http'
import { readFileSync } from 'node:fs'

const fixture = JSON.parse(readFileSync(new URL('../src/test/resources/spotify/search.json', import.meta.url), 'utf8'))
const json = (response, status, body) => {
  response.writeHead(status, { 'Content-Type': 'application/json' })
  response.end(JSON.stringify(body))
}

createServer((request, response) => {
  const url = new URL(request.url, 'http://127.0.0.1:9091')
  if (url.pathname === '/api/token' && request.method === 'POST') {
    return json(response, 200, { access_token: 'local-test-token', expires_in: 3600 })
  }
  if (url.pathname === '/v1/search' && request.method === 'GET') {
    const query = url.searchParams.get('q')
    if (query === 'error') return json(response, 500, { error: 'local-fixture-error' })
    if (query === 'empty') return json(response, 200, { albums: { items: [] } })
    if (query === 'timeout') return setTimeout(() => json(response, 200, fixture), 2500)
    return json(response, 200, fixture)
  }
  json(response, 404, { error: 'not-found' })
}).listen(9091, '127.0.0.1', () => process.stdout.write('Spotify mock: http://127.0.0.1:9091\n'))
