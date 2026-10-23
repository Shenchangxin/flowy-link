export class PairingUrl {
  version: number = 0;
  port: number = 0;
  fingerprint: string = '';
  nonce: string = '';
  addresses: string[] = [];
  name: string = '';
}

export function parsePairingUrl(raw: string): PairingUrl {
  if (!raw.startsWith('flowy://pair?')) {
    throw new Error('配对链接格式不正确');
  }
  const fields = new Map<string, string>();
  const rest = raw.substring('flowy://pair?'.length);
  for (const pair of rest.split('&')) {
    const index = pair.indexOf('=');
    if (index <= 0) {
      throw new Error('配对链接格式不正确');
    }
    fields.set(pair.substring(0, index), decodeURIComponent(pair.substring(index + 1)));
  }
  const fingerprint = (fields.get('fp') ?? '').toLowerCase();
  const nonce = (fields.get('n') ?? '').toLowerCase();
  const addresses = (fields.get('h') ?? '').split(',').map((item) => item.trim()).filter((item) => item.length > 0);
  if (!/^[0-9a-f]{64}$/.test(fingerprint) || !/^[0-9a-f]{16,128}$/.test(nonce) || addresses.length === 0) {
    throw new Error('配对链接格式不正确');
  }
  const parsed = new PairingUrl();
  parsed.version = Number(fields.get('v'));
  parsed.port = Number(fields.get('p'));
  parsed.fingerprint = fingerprint;
  parsed.nonce = nonce;
  parsed.addresses = addresses;
  parsed.name = fields.get('name') ?? '';
  if (!Number.isInteger(parsed.port) || parsed.port < 1 || parsed.port > 65535 || parsed.name.length === 0) {
    throw new Error('配对链接格式不正确');
  }
  return parsed;
}
