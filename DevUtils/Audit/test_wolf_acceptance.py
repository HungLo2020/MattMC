import copy
import sys
from pathlib import Path
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'Common'))
from wolf_acceptance import validate_document
from wolf_armor_reference import report


def document():
    fixture=dict(schema='ordinary-wolf-armor-v1',requested=True,mode='high',ready=True,
        item='minecraft:wolf_armor',count=1,damage=51,maxDamage=64,crackiness='HIGH',foil=False,baby=False,tame=False,
        x=146.6949012917378,y=99.57557514877936,z=529.4806875161336,yaw=-75.9375,headYaw=-75.9375,bodyYaw=-75.9375,
        variant='minecraft:pale')
    captures=[dict(index=i+1,window=dict(width=1280,height=720),gameTime=6000,dimension='minecraft:overworld',
        position=dict(x=150.5,y=100.0,z=530.5),shaderEnabled='false',requestedYaw=105.0,observedYaw=105.0,
        requestedPitch=10.0,observedPitch=10.0,renderedFrameIndex=100+i,screenshot=f'/capture/{i}.png') for i in range(5)]
    return dict(wolfArmorFixture=fixture,captures=captures,worldResourceReload=dict(schema='normal-world-resource-reload-v1',
        requested=True,futureComplete=True,complete=True,presentations=2,selectedBefore=['vanilla'],selectedAtCapture=['vanilla']))


class WolfAcceptanceTest(unittest.TestCase):
    def test_undamaged_fixture_requires_zero_damage(self):
        d=document();d['wolfArmorFixture'].update(mode='none',damage=0,crackiness='NONE')
        self.assertEqual(5,len(validate_document(d,mode='none')))
        for damage in (1,6,32,51,False):
            bad=copy.deepcopy(d);bad['wolfArmorFixture']['damage']=damage
            with self.assertRaises(ValueError):validate_document(bad,mode='none')
        for mode in ('high','low','medium'):
            with self.assertRaises(ValueError):validate_document(d,mode=mode)

    def test_medium_fixture_rejects_other_damage_states(self):
        d=document();d['wolfArmorFixture'].update(mode='medium',damage=32,crackiness='MEDIUM')
        self.assertEqual(5,len(validate_document(d,mode='medium')))
        for other in ('high','low','none'):
            with self.assertRaises(ValueError):validate_document(d,mode=other)
        for damage in (0,6,31,33,51,True):
            bad=copy.deepcopy(d);bad['wolfArmorFixture']['damage']=damage
            with self.assertRaises(ValueError):validate_document(bad,mode='medium')

    def test_low_and_high_fixture_damage_cannot_be_interchanged(self):
        d=document();d['wolfArmorFixture'].update(mode='low',damage=6,crackiness='LOW')
        self.assertEqual(5,len(validate_document(d,mode='low')))
        with self.assertRaises(ValueError):validate_document(d)
        with self.assertRaises(ValueError):validate_document(document(),mode='low')

    def test_document_requires_complete_fixture_reload_and_each_pose(self):
        doc=document();self.assertEqual(5,len(validate_document(doc)))
        mutations=[lambda d:d['captures'].pop(),lambda d:d['captures'].reverse(),
                   lambda d:d['wolfArmorFixture'].update(damage=50),
                   lambda d:d['worldResourceReload'].update(complete=False),
                   lambda d:d['worldResourceReload'].update(selectedAtCapture=['custom']),
                   lambda d:d['captures'][4].update(observedYaw=105.00001),
                   lambda d:d['captures'][4].update(renderedFrameIndex=100),
                   lambda d:d['captures'][4].update(screenshot='/capture/0.png'),
                   lambda d:d['captures'][4].update(shaderEnabled='true'),
                   lambda d:d['captures'][4].update(requestedPosition=dict(x=0,y=0,z=0))]
        for mutate in mutations:
            bad=copy.deepcopy(doc);mutate(bad)
            with self.assertRaises(ValueError):validate_document(bad)

    def test_report_cannot_pass_with_visual_flag_alone_or_unsupported_modes(self):
        args=['-Dmattmc.dev.graphicsAuditWolfArmor=high']
        for visual in (None,{},dict(passed=True,pairs=[]),dict(passed=False,pairs=[{}]),dict(passed=True,pairs=[{}])):
            result=report('wolf',args,visual);self.assertFalse(result['passed']);self.assertFalse(result['capability_admitted'])
        for mode in ('none','low','medium'):
            self.assertFalse(report('wolf',['-Dmattmc.dev.graphicsAuditWolfArmor='+mode],dict(passed=True,pairs=[{}]))['passed'])

if __name__=='__main__':unittest.main()
