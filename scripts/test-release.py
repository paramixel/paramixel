#!/usr/bin/env python3
# Copyright (c) 2026-present Douglas Hoard
# Licensed under the Apache License, Version 2.0.
"""Release orchestration tests. Real temporary Git repos; no external publishing."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

SOURCE = Path(__file__).resolve().parents[1]
HAS_GRADLE = "readonly HAS_GRADLE='true'" in (SOURCE / 'scripts/release.sh').read_text()
HOST = 'WWW_PARAMIXEL_ORG' if HAS_GRADLE else 'WWW_ALTCONTAINERS_ORG'
REAL_GIT = shutil.which('git')

STUB = r'''
import json, os, pathlib, re, sys
name = pathlib.Path(sys.argv[0]).name
args = sys.argv[1:]
root = pathlib.Path(os.environ['FIXTURE_ROOT'])
with open(os.environ['COMMAND_LOG'], 'a') as log:
    log.write(json.dumps([name, str(pathlib.Path.cwd()), args]) + '\n')
if name == 'git':
    if 'ls-remote' in args and os.environ.get('FAIL_REMOTE'):
        sys.exit(23)
    os.execv(os.environ['REAL_GIT'], ['git'] + args)
if name == 'mvnw':
    pom = root / 'pom.xml'
    if 'help:evaluate' in args:
        if os.environ.get('FAIL_REVISION'):
            sys.exit(24)
        print(re.search(r'<revision>(.*?)</revision>', pom.read_text())[1])
    elif 'versions:set-property' in args:
        version = next(a.split('=', 1)[1] for a in args if a.startswith('-DnewVersion='))
        pom.write_text(re.sub(r'<revision>.*?</revision>', '<revision>'+version+'</revision>', pom.read_text()))
    elif 'deploy' in args:
        if os.environ.get('FAIL_DEPLOY'):
            sys.exit(25)
    elif 'install' in args and os.environ.get('FAIL_INSTALL'):
        sys.exit(26)
    elif 'spotless:apply' in args and os.environ.get('DIRTY_FORMAT'):
        with open(root / 'pom.xml', 'a') as pom_file:
            pom_file.write('\n')
elif name == 'build-documentation.sh':
    out = root / 'website/build'
    out.mkdir(parents=True, exist_ok=True)
    (out / 'index.html').write_text('release documentation')
elif name == 'rsync' and os.environ.get('FAIL_DOCS'):
    sys.exit(27)
'''


class ReleaseTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='release-test-')
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)
        self.root = self.base / 'repo'
        self.root.mkdir()
        self.remote = self.base / 'origin.git'
        self.bin = self.base / 'bin'
        self.bin.mkdir()
        self.home = self.base / 'home'
        (self.home / '.m2').mkdir(parents=True)
        (self.home / '.m2/settings.xml').write_text('<settings/>')
        self.log = self.base / 'commands.jsonl'
        self.env = dict(os.environ, HOME=str(self.home), FIXTURE_ROOT=str(self.root),
                        COMMAND_LOG=str(self.log), REAL_GIT=REAL_GIT,
                        PATH=str(self.bin) + os.pathsep + os.environ['PATH'])
        for key in ('WWW_PARAMIXEL_ORG', 'WWW_ALTCONTAINERS_ORG', 'FAIL_DEPLOY',
                    'FAIL_DOCS', 'FAIL_REVISION', 'FAIL_REMOTE', 'FAIL_INSTALL', 'DIRTY_FORMAT'):
            self.env.pop(key, None)
        self.env[HOST] = 'test-docs-host'
        for name in ('git', 'gpg', 'rsync', 'ssh'):
            self.stub(self.bin / name)
        (self.root / 'scripts').mkdir()
        for name in ('release.sh', 'publish-documentation.sh'):
            shutil.copy2(SOURCE / 'scripts' / name, self.root / 'scripts' / name)
        for name in ('mvnw', 'gradlew', 'scripts/build-documentation.sh'):
            self.stub(self.root / name)
        (self.root / 'pom.xml').write_text('<project><properties><revision>0.9.0-POST</revision></properties></project>\n')
        (self.root / '.gitignore').write_text('website/build/\n')
        self.git('init', '-b', 'main')
        self.git('config', 'user.name', 'Release Test')
        self.git('config', 'user.email', 'release-test@example.invalid')
        self.git('config', 'commit.gpgsign', 'false')
        self.git('config', 'tag.gpgsign', 'false')
        self.git('add', '.')
        self.git('commit', '-m', 'Initial')
        self.git('init', '--bare', str(self.remote))
        self.git('remote', 'add', 'origin', str(self.remote))
        self.git('push', '-u', 'origin', 'main')
        self.log.unlink(missing_ok=True)

    def stub(self, path):
        path.write_text('#!' + sys.executable + '\n' + STUB)
        path.chmod(0o755)

    def git(self, *args):
        return subprocess.run([REAL_GIT, *args], cwd=self.root, env=self.env,
                              text=True, capture_output=True, check=True).stdout.strip()

    def run_release(self, *args, answers=None, success=True, extra_env=None):
        result = subprocess.run(['bash', str(self.root / 'scripts/release.sh'), *args],
                                cwd=self.base, env=dict(self.env, **(extra_env or {})),
                                input=answers if answers is not None else 'y\n' * 20,
                                text=True, capture_output=True, timeout=30)
        if success:
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        else:
            self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
        return result.stdout + result.stderr

    def commands(self, name=None):
        entries = [json.loads(line) for line in self.log.read_text().splitlines()] if self.log.exists() else []
        return [entry for entry in entries if name is None or entry[0] == name]

    def state(self):
        return (self.root / '.git/release-state/1.0.0').read_text().splitlines()

    def deployments(self):
        return [e for e in self.commands('mvnw') if 'deploy' in e[2]]

    def test_help_without_version(self):
        self.assertIn('Usage:', self.run_release('--help'))
        self.assertEqual(self.commands(), [])

    def test_invalid_arguments(self):
        for args in ((), ('abc',), ('1.0.0', '2.0.0'), ('1.0.0', '--unknown')):
            self.run_release(*args, success=False)

    def test_dry_run_is_offline_and_does_not_build_or_write_state(self):
        self.env.pop(HOST)
        self.run_release('1.0.0', extra_env={'FAIL_REMOTE': '1', 'FAIL_REVISION': '1'})
        self.assertFalse((self.root / '.git/release-state').exists())
        self.assertFalse((self.root / '.git/release-script.lock').exists())
        self.assertTrue(all(e[0] == 'git' and e[2] == ['rev-parse', '--git-common-dir'] for e in self.commands()))

    def test_success_from_another_directory_and_completed_rerun(self):
        self.run_release('1.0.0', '--execute')
        self.assertEqual(self.state()[0], '8')
        self.assertEqual(self.git('rev-parse', 'v1.0.0^{commit}'), self.git('rev-parse', 'release/1.0.0'))
        self.assertIn('1.0.0-POST', (self.root / 'pom.xml').read_text())
        self.assertEqual(len(self.deployments()), 1)
        self.assertEqual(len(self.commands('build-documentation.sh')), 1)
        self.assertEqual(len(self.commands('gradlew')), 2 if HAS_GRADLE else 0)
        rsync = self.commands('rsync')[0]
        self.assertEqual(rsync[2][-1], 'test-docs-host:/var/www/html/' + ('paramixel' if HAS_GRADLE else 'altcontainers') + '/')
        self.assertTrue(all(e[1] == str(self.root) for e in self.commands() if e[0] in ('mvnw', 'gradlew')))
        builds = [e for e in self.commands() if e[0] in ('mvnw', 'gradlew')]
        for i, entry in enumerate(builds):
            if any(arg in entry[2] for arg in ('install', 'deploy', 'check')):
                self.assertEqual(builds[i-1][2], ['spotless:apply'])
        self.assertIn('Signed-off-by:', self.git('log', '-1', '--format=%B'))
        self.run_release('1.0.0', '--execute')
        self.assertEqual(len(self.deployments()), 1)
        self.assertEqual(len(self.commands('rsync')), 1)

    def test_wrong_project_environment_is_rejected_before_build(self):
        self.env.pop(HOST)
        self.env['WWW_ALTCONTAINERS_ORG' if HAS_GRADLE else 'WWW_PARAMIXEL_ORG'] = 'wrong-host'
        output = self.run_release('1.0.0', '--execute', success=False)
        self.assertIn(HOST, output)
        self.assertEqual(self.commands('mvnw'), [])

    def test_docs_upload_failure_resumes_without_redeploy(self):
        self.run_release('1.0.0', '--execute', success=False, extra_env={'FAIL_DOCS': '1'})
        self.assertEqual(self.state()[0], '6')
        self.assertEqual(self.git('branch', '--show-current'), 'release/1.0.0')
        shutil.rmtree(self.root / 'website/build')
        self.run_release('1.0.0', '--execute')
        self.assertEqual(self.state()[0], '8')
        self.assertEqual(len(self.deployments()), 1)
        self.assertEqual(len(self.commands('build-documentation.sh')), 2)

    def test_pending_publication_preserves_branches_and_does_not_redeploy(self):
        self.run_release('1.0.0', '--execute', answers='y\ny\nn\n', success=False)
        self.assertEqual(self.state()[0], '4')
        self.assertEqual(self.git('rev-parse', 'release/1.0.0'), self.git('rev-parse', 'origin/release/1.0.0'))
        self.run_release('1.0.0', '--execute')
        self.assertEqual(len(self.deployments()), 1)

    def test_failed_deploy_requires_portal_verification(self):
        self.run_release('1.0.0', '--execute', success=False, extra_env={'FAIL_DEPLOY': '1'})
        self.assertEqual(self.state()[0], '4')
        self.run_release('1.0.0', '--execute', answers='n\n', success=False)
        self.assertEqual(len(self.deployments()), 1)
        self.run_release('1.0.0', '--execute', '--retry-deploy', answers='n\n', success=False)
        self.assertEqual(len(self.deployments()), 1)
        self.run_release('1.0.0', '--execute', '--retry-deploy')
        self.assertEqual(len(self.deployments()), 2)

    def test_cancelled_tag_push_resumes(self):
        self.run_release('1.0.0', '--execute', answers='y\ny\ny\nn\n', success=False)
        self.assertEqual(self.state()[0], '5')
        self.run_release('1.0.0', '--execute')
        self.assertEqual(len(self.deployments()), 1)

    def test_tag_pushed_before_checkpoint_update_is_reused(self):
        self.run_release('1.0.0', '--execute', answers='y\ny\ny\nn\n', success=False)
        self.git('push', 'origin', 'refs/tags/v1.0.0')
        self.run_release('1.0.0', '--execute')
        self.assertEqual(len(self.deployments()), 1)

    def test_cancelled_main_push_resumes(self):
        self.run_release('1.0.0', '--execute', answers='y\ny\ny\ny\nn\n', success=False)
        self.assertEqual(self.state()[0], '7')
        self.run_release('1.0.0', '--execute')
        self.assertEqual(len(self.deployments()), 1)

    def test_ci_fixes_can_be_confirmed_on_resume(self):
        self.run_release('1.0.0', '--execute', answers='y\nn\n', success=False)
        self.git('commit', '--allow-empty', '-m', 'CI fix')
        self.run_release('1.0.0', '--execute')
        self.assertEqual(self.git('rev-parse', 'v1.0.0^{commit}'), self.git('rev-parse', 'release/1.0.0'))

    def test_changed_release_after_ci_is_rejected(self):
        self.run_release('1.0.0', '--execute', success=False, extra_env={'FAIL_DEPLOY': '1'})
        self.git('commit', '--allow-empty', '-m', 'Unexpected change')
        self.run_release('1.0.0', '--execute', success=False)
        self.assertEqual(len(self.deployments()), 1)

    def test_remote_tag_conflict_is_rejected(self):
        self.run_release('1.0.0', '--execute', success=False, extra_env={'FAIL_DOCS': '1'})
        self.git('tag', '-f', 'v1.0.0', 'main')
        self.git('push', '--force', 'origin', 'refs/tags/v1.0.0')
        self.git('tag', '-d', 'v1.0.0')
        self.assertIn('conflicts', self.run_release('1.0.0', '--execute', success=False))

    def test_preexisting_tag_without_state_is_rejected(self):
        self.git('tag', 'v1.0.0')
        self.assertIn('Recover manually', self.run_release('1.0.0', '--execute', success=False))
        self.assertEqual(self.deployments(), [])

    def test_remote_lookup_failure_is_not_tag_absence(self):
        self.assertIn('Cannot query remote tags', self.run_release('1.0.0', '--execute', success=False, extra_env={'FAIL_REMOTE': '1'}))
        self.assertEqual(self.commands('mvnw'), [])

    def test_stale_main_tracking_ref_is_refreshed(self):
        self.git('commit', '--allow-empty', '-m', 'New origin commit')
        self.git('push', 'origin', 'main')
        self.git('reset', '--hard', 'HEAD~1')
        self.git('update-ref', 'refs/remotes/origin/main', 'HEAD')
        self.assertIn('not synced', self.run_release('1.0.0', '--execute', success=False))

    def test_skip_docs_build_reuses_existing_output(self):
        (self.root / 'website/build').mkdir(parents=True)
        (self.root / 'website/build/index.html').write_text('prebuilt')
        self.run_release('1.0.0', '--execute', '--skip-docs-build')
        self.assertEqual(self.commands('build-documentation.sh'), [])

    def test_missing_skipped_docs_build_fails_before_deploy(self):
        self.run_release('1.0.0', '--execute', '--skip-docs-build', success=False)
        self.assertEqual(self.deployments(), [])

    def test_revision_failure_aborts_before_mutation(self):
        self.run_release('1.0.0', '--execute', success=False, extra_env={'FAIL_REVISION': '1'})
        self.assertIn('0.9.0-POST', (self.root / 'pom.xml').read_text())
        self.assertFalse(any('versions:set-property' in e[2] for e in self.commands('mvnw')))

    def test_existing_release_revision_is_read_after_checkout(self):
        self.git('checkout', '-b', 'release/1.0.0')
        self.git('checkout', 'main')
        pom = self.root / 'pom.xml'
        pom.write_text(pom.read_text().replace('0.9.0-POST', '1.0.0'))
        self.git('commit', '-am', 'Main already has version')
        self.git('push', 'origin', 'main')
        self.run_release('1.0.0', '--execute')
        self.assertIn('<revision>1.0.0</revision>', self.git('show', 'v1.0.0:pom.xml'))

    def test_lock_and_corrupt_state_fail_closed(self):
        lock = self.root / '.git/release-script.lock'
        lock.mkdir()
        self.assertIn('lock exists', self.run_release('1.0.0', '--execute', success=False))
        lock.rmdir()
        state = self.root / '.git/release-state/1.0.0'
        state.parent.mkdir()
        state.write_text('4\n$(touch unsafe)\n')
        self.assertIn('Invalid checkpoint', self.run_release('1.0.0', '--execute', success=False))
        self.assertFalse((self.root / 'unsafe').exists())

    def test_local_build_failure_resumes_after_reviewed_changes(self):
        self.run_release('1.0.0', '--execute', success=False, extra_env={'FAIL_INSTALL': '1'})
        self.assertEqual(self.state()[0], '1')
        self.assertEqual(self.deployments(), [])
        self.run_release('1.0.0', '--execute', success=False)
        self.git('commit', '-am', 'Reviewed release version')
        self.run_release('1.0.0', '--execute')
        self.assertEqual(len(self.deployments()), 1)

    def test_dirty_formatting_prevents_deployment(self):
        self.run_release('1.0.0', '--execute', success=False, extra_env={'DIRTY_FORMAT': '1'})
        self.assertEqual(self.state()[0], '3')
        self.assertEqual(self.deployments(), [])

    def test_branch_with_tag_name_is_not_a_tag(self):
        self.git('branch', 'v1.0.0')
        self.run_release('1.0.0', '--execute')
        self.assertEqual(len(self.deployments()), 1)

    def test_remote_release_change_blocks_publication_resume(self):
        self.run_release('1.0.0', '--execute', success=False, extra_env={'FAIL_DEPLOY': '1'})
        original = self.git('rev-parse', 'HEAD')
        self.git('commit', '--allow-empty', '-m', 'Remote release changed')
        self.git('push', 'origin', 'release/1.0.0')
        self.git('reset', '--hard', original)
        self.assertIn('does not match', self.run_release('1.0.0', '--execute', success=False))
        self.assertEqual(len(self.deployments()), 1)

    def test_eof_at_publication_preserves_pending_release(self):
        self.run_release('1.0.0', '--execute', answers='y\ny\n', success=False)
        self.assertEqual(self.state()[0], '4')
        self.assertEqual(len(self.deployments()), 1)

    def test_gradle_option(self):
        self.run_release('1.0.0', '--execute', '--skip-gradle', success=HAS_GRADLE)
        self.assertEqual(self.commands('gradlew'), [])


if __name__ == '__main__':
    unittest.main()
