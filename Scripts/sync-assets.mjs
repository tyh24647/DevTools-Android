import fs from 'node:fs/promises';
import path from 'node:path';
const root=path.resolve(import.meta.dirname,'..');
const from=path.join(root,'SafariExtension/Resources');
const target=path.join(root,'app/src/main/assets/tools');
await fs.cp(path.join(from,'vendor'),path.join(target,'vendor'),{recursive:true});
await fs.copyFile(path.join(from,'tool-catalog.json'),path.join(target,'tool-catalog.json'));
await fs.copyFile(path.join(root,'Shared/THIRD-PARTY-NOTICES.txt'),path.join(root,'app/src/main/assets/THIRD-PARTY-NOTICES.txt'));
console.log('Android bundled tools and notices refreshed.');
