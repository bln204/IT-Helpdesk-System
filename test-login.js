const accounts = [
  { username: 'admin', password: '12345678' },
  { username: 'mayagiamdoc', password: '12345678' },
  { username: 'tp.it', password: '12345678' },
  { username: 'tp.hr', password: '12345678' },
  { username: 'tp.sale', password: '12345678' },
  { username: 'tp.mkt', password: '12345678' },
  { username: 'tp.fin', password: '12345678' },
  { username: 'tech.smith', password: '12345678' },
  { username: 'tech.jane', password: '12345678' },
  { username: 'hr.tina', password: '12345678' },
  { username: 'hr.bob', password: '12345678' },
  { username: 'sale.alice', password: '12345678' },
  { username: 'sale.david', password: '12345678' },
  { username: 'mkt.chris', password: '12345678' },
  { username: 'mkt.eva', password: '12345678' },
  { username: 'fin.anna', password: '12345678' },
  { username: 'fin.tom', password: '12345678' },
];

async function testLogin(username, password) {
  try {
    const response = await fetch('http://localhost:8080/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password })
    });
    const data = await response.json();
    if (response.ok && data.token) {
      // Test GET /me with token
      const meResponse = await fetch('http://localhost:8080/api/users/me', {
        headers: { 'Authorization': 'Bearer ' + data.token }
      });
      if (meResponse.ok) {
        const me = await meResponse.json();
        return { status: 'OK', role: me.role };
      } else {
        return { status: 'ERROR', detail: 'Token OK but GET /me failed: ' + meResponse.status };
      }
    } else {
      return { status: 'ERROR', detail: data.message || 'No token' };
    }
  } catch (err) {
    return { status: 'ERROR', detail: err.message };
  }
}

async function runTests() {
  console.log('Testing all accounts...\n');
  const results = [];
  
  for (const acc of accounts) {
    const result = await testLogin(acc.username, acc.password);
    results.push({ ...acc, ...result });
    console.log(`${acc.username}: ${result.status} ${result.role ? '(' + result.role + ')' : ''} ${result.detail || ''}`);
  }
  
  console.log('\n=== Summary ===');
  const ok = results.filter(r => r.status === 'OK').length;
  const fail = results.filter(r => r.status !== 'OK').length;
  console.log(`OK: ${ok}, Failed: ${fail}`);
  if (fail > 0) {
    console.log('\nFailed accounts:');
    results.filter(r => r.status !== 'OK').forEach(r => {
      console.log(`  - ${r.username}: ${r.detail}`);
    });
  }
}

runTests();
