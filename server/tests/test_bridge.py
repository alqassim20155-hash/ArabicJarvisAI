from unittest.mock import patch
from fastapi.testclient import TestClient
from server import bridge

client = TestClient(bridge.app)

def test_auth_required():
    assert client.post('/v1/ask', json={'message':'مرحبا'}).status_code == 401

def test_real_cli_adapter_mocked():
    bridge.TOKEN = 'test-token'
    with patch.object(bridge.subprocess, 'run') as run:
        run.return_value.returncode = 0
        run.return_value.stdout = 'أهلاً'
        r = client.post('/v1/ask', json={'message':'مرحبا'}, headers={'Authorization':'Bearer test-token'})
        assert r.status_code == 200 and r.json()['reply'] == 'أهلاً'
        assert run.call_args.args[0][1:] == ['ask', 'مرحبا']
        assert run.call_args.kwargs['stdin'] == bridge.subprocess.DEVNULL

def test_input_limit():
    bridge.TOKEN = 'test-token'
    assert client.post('/v1/ask', json={'message':'x'*3001}, headers={'Authorization':'Bearer test-token'}).status_code == 422
