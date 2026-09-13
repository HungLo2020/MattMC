"""Frozen-derived validation for ordinary GUI entity-preview producers."""
import copy
from pathlib import Path
from PIL import Image, ImageChops, ImageStat
from held_special_foil_reference import require, integer

HORSE_CROP = (450, 160, 614, 324)
SMITHING_CROP = (720, 150, 880, 370)


def validate_horse_fixture(receipt):
    require(isinstance(receipt, dict)
            and receipt.get('schema') == 'entity-preview-fixture-v1'
            and receipt.get('requested') is True and receipt.get('mode') == 'horse'
            and integer(receipt.get('entityId'), 1)
            and all(receipt.get(key) is True for key in
                ('serverSpawned', 'clientReplicated', 'screenOpen', 'mouseCentered', 'previewReady', 'complete')),
            'horse entity-preview fixture is incomplete')


def validate_equipped_horse_fixture(receipt):
    validate_horse_fixture({**receipt, 'mode': 'horse'})
    require(receipt.get('mode') == 'horse-equipped'
            and receipt.get('horseEquipmentPopulated') is True
            and receipt.get('horseBodyItem') == 'minecraft:diamond_horse_armor'
            and receipt.get('horseSaddleItem') == 'minecraft:saddle',
            'equipped horse preview is incomplete')


def validate_black_horse_fixture(receipt):
    validate_horse_fixture({**receipt, 'mode': 'horse'})
    require(receipt.get('mode') == 'horse-black'
            and receipt.get('horseStateReady') is True
            and receipt.get('horseBaby') is False
            and receipt.get('horseEquipmentPopulated') is False
            and receipt.get('horseVariant') == 'black'
            and receipt.get('horseMarkings') == 'none'
            and receipt.get('horseBodyItem') == ''
            and receipt.get('horseSaddleItem') == '',
            'black horse preview is incomplete')


def validate_brown_horse_fixture(receipt):
    validate_horse_fixture({**receipt, 'mode':'horse'})
    require(receipt.get('mode') == 'horse-brown'
            and receipt.get('horseStateReady') is True
            and receipt.get('horseBaby') is False
            and receipt.get('horseEquipmentPopulated') is False
            and receipt.get('horseVariant') == 'brown'
            and receipt.get('horseMarkings') == 'none'
            and receipt.get('horseBodyItem') == '' and receipt.get('horseSaddleItem') == '',
            'brown horse preview is incomplete')


def validate_creamy_horse_fixture(receipt):
    validate_horse_fixture({**receipt, 'mode':'horse'})
    require(receipt.get('mode') == 'horse-creamy'
            and receipt.get('horseStateReady') is True and receipt.get('horseBaby') is False
            and receipt.get('horseEquipmentPopulated') is False
            and receipt.get('horseVariant') == 'creamy' and receipt.get('horseMarkings') == 'none'
            and receipt.get('horseBodyItem') == '' and receipt.get('horseSaddleItem') == '',
            'creamy horse preview is incomplete')


def validate_chestnut_horse_fixture(receipt):
    validate_horse_fixture({**receipt, 'mode':'horse'})
    require(receipt.get('mode') == 'horse-chestnut'
            and receipt.get('horseStateReady') is True and receipt.get('horseBaby') is False
            and receipt.get('horseEquipmentPopulated') is False
            and receipt.get('horseVariant') == 'chestnut' and receipt.get('horseMarkings') == 'none'
            and receipt.get('horseBodyItem') == '' and receipt.get('horseSaddleItem') == '',
            'chestnut horse preview is incomplete')


def validate_gray_horse_fixture(receipt):
    validate_horse_fixture({**receipt, 'mode':'horse'})
    require(receipt.get('mode') == 'horse-gray'
            and receipt.get('horseStateReady') is True and receipt.get('horseBaby') is False
            and receipt.get('horseEquipmentPopulated') is False
            and receipt.get('horseVariant') == 'gray' and receipt.get('horseMarkings') == 'none'
            and receipt.get('horseBodyItem') == '' and receipt.get('horseSaddleItem') == '',
            'gray horse preview is incomplete')


def validate_dark_brown_horse_fixture(receipt):
    validate_horse_fixture({**receipt, 'mode':'horse'})
    require(receipt.get('mode') == 'horse-dark-brown'
            and receipt.get('horseStateReady') is True and receipt.get('horseBaby') is False
            and receipt.get('horseEquipmentPopulated') is False
            and receipt.get('horseVariant') == 'dark_brown' and receipt.get('horseMarkings') == 'none'
            and receipt.get('horseBodyItem') == '' and receipt.get('horseSaddleItem') == '',
            'dark brown horse preview is incomplete')


def validate_saddled_skeleton_horse_fixture(receipt):
    validate_horse_fixture({**receipt, 'mode': 'horse'})
    require(receipt.get('mode') == 'skeleton-horse-saddled'
            and receipt.get('horseStateReady') is True
            and receipt.get('equineType') == 'minecraft:skeleton_horse'
            and receipt.get('horseBaby') is False
            and receipt.get('horseEquipmentPopulated') is False
            and receipt.get('horseBodyItem') == ''
            and receipt.get('horseSaddleItem') == 'minecraft:saddle',
            'saddled skeleton horse preview is incomplete')


def validate_saddled_baby_skeleton_horse_fixture(receipt):
    validate_saddled_skeleton_horse_fixture({**receipt, 'mode': 'skeleton-horse-saddled',
        'horseBaby': False})
    require(receipt.get('mode') == 'skeleton-horse-baby-saddled'
            and receipt.get('horseStateReady') is True and receipt.get('horseBaby') is True,
            'saddled baby skeleton horse preview is incomplete')


def validate_saddled_zombie_horse_fixture(receipt):
    validate_horse_fixture({**receipt, 'mode': 'horse'})
    require(receipt.get('mode') == 'zombie-horse-saddled'
            and receipt.get('horseStateReady') is True
            and receipt.get('equineType') == 'minecraft:zombie_horse'
            and receipt.get('horseBaby') is False
            and receipt.get('horseEquipmentPopulated') is False
            and receipt.get('horseBodyItem') == ''
            and receipt.get('horseSaddleItem') == 'minecraft:saddle',
            'saddled zombie horse preview is incomplete')


def validate_saddled_baby_zombie_horse_fixture(receipt):
    validate_saddled_zombie_horse_fixture({**receipt, 'mode':'zombie-horse-saddled', 'horseBaby':False})
    require(receipt.get('mode') == 'zombie-horse-baby-saddled'
            and receipt.get('horseStateReady') is True and receipt.get('horseBaby') is True,
            'saddled baby zombie horse preview is incomplete')


def validate_iron_equipped_horse_fixture(receipt):
    validate_equipped_horse_fixture({**receipt, 'mode': 'horse-equipped',
        'horseBodyItem': 'minecraft:diamond_horse_armor'})
    require(receipt.get('mode') == 'horse-iron-equipped'
            and receipt.get('horseEquipmentPopulated') is True
            and receipt.get('horseBodyItem') == 'minecraft:iron_horse_armor'
            and receipt.get('horseSaddleItem') == 'minecraft:saddle',
            'iron-equipped horse preview is incomplete')


def validate_gold_equipped_horse_fixture(receipt):
    validate_equipped_horse_fixture({**receipt, 'mode': 'horse-equipped',
        'horseBodyItem': 'minecraft:diamond_horse_armor'})
    require(receipt.get('mode') == 'horse-gold-equipped'
            and receipt.get('horseEquipmentPopulated') is True
            and receipt.get('horseBodyItem') == 'minecraft:golden_horse_armor'
            and receipt.get('horseSaddleItem') == 'minecraft:saddle',
            'gold-equipped horse preview is incomplete')


def validate_copper_equipped_horse_fixture(receipt):
    validate_equipped_horse_fixture({**receipt, 'mode': 'horse-equipped',
        'horseBodyItem': 'minecraft:diamond_horse_armor'})
    require(receipt.get('mode') == 'horse-copper-equipped'
            and receipt.get('horseEquipmentPopulated') is True
            and receipt.get('horseBodyItem') == 'minecraft:copper_horse_armor'
            and receipt.get('horseSaddleItem') == 'minecraft:saddle',
            'copper-equipped horse preview is incomplete')


def validate_leather_equipped_horse_fixture(receipt):
    validate_equipped_horse_fixture({**receipt, 'mode': 'horse-equipped',
        'horseBodyItem': 'minecraft:diamond_horse_armor'})
    require(receipt.get('mode') == 'horse-leather-equipped'
            and receipt.get('horseEquipmentPopulated') is True
            and receipt.get('horseBodyItem') == 'minecraft:leather_horse_armor'
            and receipt.get('horseSaddleItem') == 'minecraft:saddle',
            'leather-equipped horse preview is incomplete')



def validate_dyed_leather_equipped_horse_fixture(receipt):
    validate_leather_equipped_horse_fixture({**receipt, 'mode': 'horse-leather-equipped'})
    require(receipt.get('mode') == 'horse-dyed-leather-equipped'
            and receipt.get('horseBodyDyeRgb') == 0x3366CC,
            'dyed-leather-equipped horse preview is incomplete')


def validate_baby_horse_fixture(receipt):
    validate_horse_fixture({**receipt, 'mode': 'horse'})
    require(receipt.get('mode') == 'horse-baby'
            and receipt.get('horseStateReady') is True
            and receipt.get('horseBaby') is True
            and receipt.get('horseEquipmentPopulated') is False
            and receipt.get('horseBodyItem') == ''
            and receipt.get('horseSaddleItem') == '',
            'baby horse preview is incomplete')


def validate_equipped_baby_horse_fixture(receipt):
    validate_equipped_horse_fixture({**receipt, 'mode': 'horse-equipped'})
    require(receipt.get('mode') == 'horse-baby-equipped'
            and receipt.get('horseStateReady') is True and receipt.get('horseBaby') is True,
            'equipped baby horse preview is incomplete')


def validate_iron_equipped_baby_horse_fixture(receipt):
    validate_equipped_baby_horse_fixture({**receipt, 'mode': 'horse-baby-equipped',
        'horseBodyItem': 'minecraft:diamond_horse_armor'})
    require(receipt.get('mode') == 'horse-baby-iron-equipped'
            and receipt.get('horseEquipmentPopulated') is True
            and receipt.get('horseBodyItem') == 'minecraft:iron_horse_armor'
            and receipt.get('horseSaddleItem') == 'minecraft:saddle',
            'iron-equipped baby horse preview is incomplete')


def validate_gold_equipped_baby_horse_fixture(receipt):
    validate_equipped_baby_horse_fixture({**receipt, 'mode': 'horse-baby-equipped',
        'horseBodyItem': 'minecraft:diamond_horse_armor'})
    require(receipt.get('mode') == 'horse-baby-gold-equipped'
            and receipt.get('horseEquipmentPopulated') is True
            and receipt.get('horseBodyItem') == 'minecraft:golden_horse_armor'
            and receipt.get('horseSaddleItem') == 'minecraft:saddle',
            'gold-equipped baby horse preview is incomplete')


def validate_copper_equipped_baby_horse_fixture(receipt):
    validate_equipped_baby_horse_fixture({**receipt, 'mode': 'horse-baby-equipped',
        'horseBodyItem': 'minecraft:diamond_horse_armor'})
    require(receipt.get('mode') == 'horse-baby-copper-equipped'
            and receipt.get('horseEquipmentPopulated') is True
            and receipt.get('horseBodyItem') == 'minecraft:copper_horse_armor'
            and receipt.get('horseSaddleItem') == 'minecraft:saddle',
            'copper-equipped baby horse preview is incomplete')


def validate_leather_equipped_baby_horse_fixture(receipt):
    validate_equipped_baby_horse_fixture({**receipt, 'mode': 'horse-baby-equipped',
        'horseBodyItem': 'minecraft:diamond_horse_armor'})
    require(receipt.get('mode') == 'horse-baby-leather-equipped'
            and receipt.get('horseEquipmentPopulated') is True
            and receipt.get('horseBodyItem') == 'minecraft:leather_horse_armor'
            and receipt.get('horseSaddleItem') == 'minecraft:saddle',
            'leather-equipped baby horse preview is incomplete')



def validate_dyed_leather_equipped_baby_horse_fixture(receipt):
    validate_leather_equipped_baby_horse_fixture({**receipt, 'mode': 'horse-baby-leather-equipped'})
    require(receipt.get('mode') == 'horse-baby-dyed-leather-equipped'
            and receipt.get('horseBodyDyeRgb') == 0x3366CC,
            'dyed-leather-equipped baby horse preview is incomplete')


def validate_marked_horse_fixture(receipt):
    validate_horse_fixture({**receipt, 'mode': 'horse'})
    require(receipt.get('mode') == 'horse-marked'
            and receipt.get('horseStateReady') is True
            and receipt.get('horseBaby') is False
            and receipt.get('horseEquipmentPopulated') is False
            and receipt.get('horseVariant') == 'white'
            and receipt.get('horseMarkings') == 'white_dots'
            and receipt.get('horseBodyItem') == ''
            and receipt.get('horseSaddleItem') == '',
            'marked horse preview is incomplete')


def validate_white_marking_horse_fixture(receipt):
    validate_horse_fixture({**receipt, 'mode': 'horse'})
    require(receipt.get('mode') == 'horse-marking-white'
            and receipt.get('horseStateReady') is True and receipt.get('horseBaby') is False
            and receipt.get('horseEquipmentPopulated') is False
            and receipt.get('horseVariant') == 'white' and receipt.get('horseMarkings') == 'white'
            and receipt.get('horseBodyItem') == '' and receipt.get('horseSaddleItem') == '',
            'white-marking horse preview is incomplete')


def validate_white_field_horse_fixture(receipt):
    validate_horse_fixture({**receipt, 'mode': 'horse'})
    require(receipt.get('mode') == 'horse-marking-white-field'
            and receipt.get('horseStateReady') is True and receipt.get('horseBaby') is False
            and receipt.get('horseEquipmentPopulated') is False
            and receipt.get('horseVariant') == 'white' and receipt.get('horseMarkings') == 'white_field'
            and receipt.get('horseBodyItem') == '' and receipt.get('horseSaddleItem') == '',
            'white-field horse preview is incomplete')


def validate_black_dots_horse_fixture(receipt):
    validate_horse_fixture({**receipt, 'mode': 'horse'})
    require(receipt.get('mode') == 'horse-marking-black-dots'
            and receipt.get('horseStateReady') is True and receipt.get('horseBaby') is False
            and receipt.get('horseEquipmentPopulated') is False
            and receipt.get('horseVariant') == 'white' and receipt.get('horseMarkings') == 'black_dots'
            and receipt.get('horseBodyItem') == '' and receipt.get('horseSaddleItem') == '',
            'black-dots horse preview is incomplete')


def validate_baby_marked_horse_fixture(receipt):
    validate_marked_horse_fixture({**receipt, 'mode': 'horse-marked', 'horseBaby': False})
    require(receipt.get('mode') == 'horse-baby-marked'
            and receipt.get('horseStateReady') is True and receipt.get('horseBaby') is True,
            'marked baby horse preview is incomplete')


def validate_equipped_marked_horse_fixture(receipt):
    validate_marked_horse_fixture({**receipt, 'mode': 'horse-marked',
        'horseEquipmentPopulated': False, 'horseBodyItem': '', 'horseSaddleItem': ''})
    require(receipt.get('mode') == 'horse-marked-equipped'
            and receipt.get('horseEquipmentPopulated') is True
            and receipt.get('horseBodyItem') == 'minecraft:diamond_horse_armor'
            and receipt.get('horseSaddleItem') == 'minecraft:saddle',
            'equipped marked horse preview is incomplete')


def validate_equipped_baby_marked_horse_fixture(receipt):
    validate_equipped_marked_horse_fixture({**receipt, 'mode': 'horse-marked-equipped',
        'horseBaby': False})
    require(receipt.get('mode') == 'horse-baby-marked-equipped'
            and receipt.get('horseStateReady') is True and receipt.get('horseBaby') is True,
            'equipped marked baby horse preview is incomplete')


def validate_chested_donkey_fixture(receipt):
    validate_horse_fixture({**receipt, 'mode': 'horse'})
    require(receipt.get('mode') == 'donkey-chested-equipped'
            and receipt.get('horseStateReady') is True
            and receipt.get('equineType') == 'minecraft:donkey'
            and receipt.get('horseBaby') is False
            and receipt.get('horseHasChest') is True
            and receipt.get('horseInventoryColumns') == 5
            and receipt.get('horseBodyItem') == ''
            and receipt.get('horseSaddleItem') == 'minecraft:saddle',
            'chested saddled donkey preview is incomplete')


def validate_baby_chested_donkey_fixture(receipt):
    validate_chested_donkey_fixture({**receipt, 'mode': 'donkey-chested-equipped',
        'horseBaby': False})
    require(receipt.get('mode') == 'donkey-baby-chested-equipped'
            and receipt.get('horseStateReady') is True and receipt.get('horseBaby') is True,
            'baby chested saddled donkey preview is incomplete')


def validate_chested_mule_fixture(receipt):
    validate_horse_fixture({**receipt, 'mode': 'horse'})
    require(receipt.get('mode') == 'mule-chested-equipped'
            and receipt.get('horseStateReady') is True
            and receipt.get('equineType') == 'minecraft:mule'
            and receipt.get('horseBaby') is False
            and receipt.get('horseHasChest') is True
            and receipt.get('horseInventoryColumns') == 5
            and receipt.get('horseBodyItem') == ''
            and receipt.get('horseSaddleItem') == 'minecraft:saddle',
            'chested saddled mule preview is incomplete')


def validate_baby_chested_mule_fixture(receipt):
    validate_chested_mule_fixture({**receipt, 'mode': 'mule-chested-equipped',
        'horseBaby': False})
    require(receipt.get('mode') == 'mule-baby-chested-equipped'
            and receipt.get('horseStateReady') is True and receipt.get('horseBaby') is True,
            'baby chested saddled mule preview is incomplete')


def validate_chested_blue_llama_fixture(receipt):
    validate_horse_fixture({**receipt, 'mode': 'horse'})
    require(receipt.get('mode') == 'llama-chested-blue-carpet'
            and receipt.get('horseStateReady') is True
            and receipt.get('equineType') == 'minecraft:llama'
            and receipt.get('horseBaby') is False
            and receipt.get('horseHasChest') is True
            and receipt.get('horseInventoryColumns') == 5
            and receipt.get('llamaVariant') == 'creamy'
            and receipt.get('llamaStrength') == 5
            and receipt.get('llamaDecorPopulated') is True
            and receipt.get('horseBodyItem') == 'minecraft:blue_carpet'
            and receipt.get('horseSaddleItem') == '',
            'chested blue-carpet llama preview is incomplete')


def validate_baby_chested_blue_llama_fixture(receipt):
    validate_chested_blue_llama_fixture({**receipt, 'mode': 'llama-chested-blue-carpet',
        'horseBaby': False})
    require(receipt.get('mode') == 'llama-baby-chested-blue-carpet'
            and receipt.get('horseStateReady') is True and receipt.get('horseBaby') is True,
            'baby chested blue-carpet llama preview is incomplete')


def validate_smithing_fixture(receipt):
    require(isinstance(receipt, dict)
            and receipt.get('schema') == 'entity-preview-fixture-v1'
            and receipt.get('requested') is True and receipt.get('mode') == 'smithing'
            and receipt.get('serverSpawned') is False and receipt.get('entityId') == -1
            and receipt.get('clientReplicated') is False
            and all(receipt.get(key) is True for key in
                ('serverMenuOpened', 'screenOpen', 'mouseCentered', 'previewReady', 'complete')),
            'smithing entity-preview fixture is incomplete')


def validate_equipped_smithing_fixture(receipt):
    validate_smithing_fixture({**receipt, 'mode': 'smithing'})
    require(receipt.get('mode') == 'smithing-netherite-chestplate'
            and receipt.get('recipePopulated') is True
            and receipt.get('resultItem') == 'minecraft:netherite_chestplate',
            'equipped smithing result is incomplete')


def paired_horse_preview_inputs(frozen, current, frozen_frame, current_frame):
    expected = dict(bounds=[151.0, 55.0, 203.0, 107.0], translation=[0.0, 1.05, 0.0],
        rotation=[0.0, -0.13446018, 0.990919, 0.0],
        cameraRotation=[-0.13446018, 0.0, 0.0, 0.990919], scales=[17.0, 1.0, 1.0],
        angles=[165.3437, -14.656311, 15.454812], invisible=False,
        invisibleToPlayer=False, pose='STANDING', light=15728880)
    normalized = []
    ages = []
    for receipt, frame in ((frozen, frozen_frame), (current, current_frame)):
        require(isinstance(receipt, dict) and receipt.get('schema') == 'inventory-preview-inputs-v1'
                and receipt.get('enabled') is True and receipt.get('complete') is True
                and receipt.get('capabilityAdmitted') is False and receipt.get('viewStable') is True
                and receipt.get('renderedFrameIndex') == frame,
                'horse preview inputs are missing or stale')
        observations = receipt.get('observations')
        require(isinstance(observations, list) and len(observations) == 1,
                'horse preview requires one observed entity')
        row = copy.deepcopy(observations[0])
        animation = row.pop('animation', None)
        require(row == expected and isinstance(animation, list) and len(animation) == 4
                and type(animation[0]) in (int, float) and animation[0] >= 0
                and animation[1:] == [0.0, 0.0, 0.0],
                'horse preview source inputs differ from the inspected Frozen scope')
        require(receipt.get('screenMouse') == [
            {'stage':'background','stored':[213.0,120.0],'supplied':[213.0,120.0]},
            {'stage':'render-return','stored':[213.0,120.0],'supplied':[213.0,120.0]}],
            'horse preview cursor inputs differ from the inspected Frozen scope')
        normalized.append(row)
        ages.append(animation)
    require(normalized[0] == normalized[1], 'horse preview non-animation inputs differ')
    return dict(passed=True, capability_admitted=False, ordinary_non_driving_age=ages)



def paired_dyed_leather_equipped_horse_preview_inputs(frozen,current,frozen_frame,current_frame):
    return paired_horse_preview_inputs(frozen,current,frozen_frame,current_frame)


def paired_black_horse_preview_inputs(frozen,current,frozen_frame,current_frame):
    return paired_horse_preview_inputs(frozen,current,frozen_frame,current_frame)


def paired_brown_horse_preview_inputs(frozen,current,frozen_frame,current_frame):
    return paired_horse_preview_inputs(frozen,current,frozen_frame,current_frame)


def paired_creamy_horse_preview_inputs(frozen,current,frozen_frame,current_frame):
    return paired_horse_preview_inputs(frozen,current,frozen_frame,current_frame)


def paired_chestnut_horse_preview_inputs(frozen,current,frozen_frame,current_frame):
    return paired_horse_preview_inputs(frozen,current,frozen_frame,current_frame)


def paired_gray_horse_preview_inputs(frozen,current,frozen_frame,current_frame):
    return paired_horse_preview_inputs(frozen,current,frozen_frame,current_frame)


def paired_dark_brown_horse_preview_inputs(frozen,current,frozen_frame,current_frame):
    return paired_horse_preview_inputs(frozen,current,frozen_frame,current_frame)


def paired_white_marking_horse_preview_inputs(frozen,current,frozen_frame,current_frame):
    return paired_horse_preview_inputs(frozen,current,frozen_frame,current_frame)


def paired_white_field_horse_preview_inputs(frozen,current,frozen_frame,current_frame):
    return paired_horse_preview_inputs(frozen,current,frozen_frame,current_frame)


def paired_black_dots_horse_preview_inputs(frozen,current,frozen_frame,current_frame):
    return paired_horse_preview_inputs(frozen,current,frozen_frame,current_frame)


def paired_saddled_skeleton_horse_preview_inputs(frozen,current,frozen_frame,current_frame):
    return paired_horse_preview_inputs(frozen,current,frozen_frame,current_frame)


def paired_saddled_baby_skeleton_horse_preview_inputs(frozen,current,frozen_frame,current_frame):
    return paired_baby_horse_preview_inputs(frozen,current,frozen_frame,current_frame)


def paired_saddled_zombie_horse_preview_inputs(frozen,current,frozen_frame,current_frame):
    return paired_horse_preview_inputs(frozen,current,frozen_frame,current_frame)


def paired_saddled_baby_zombie_horse_preview_inputs(frozen,current,frozen_frame,current_frame):
    return paired_baby_horse_preview_inputs(frozen,current,frozen_frame,current_frame)


def paired_baby_horse_preview_inputs(frozen, current, frozen_frame, current_frame):
    expected = dict(bounds=[151.0, 55.0, 203.0, 107.0], translation=[0.0, 0.65, 0.0],
        rotation=[0.0, -0.13446018, 0.990919, 0.0],
        cameraRotation=[-0.13446018, 0.0, 0.0, 0.990919], scales=[17.0, 1.0, 0.5],
        angles=[165.3437, -14.656311, 15.454812], invisible=False,
        invisibleToPlayer=False, pose='STANDING', light=15728880)
    ages=[]
    for receipt,frame in ((frozen,frozen_frame),(current,current_frame)):
        require(isinstance(receipt,dict) and receipt.get('schema')=='inventory-preview-inputs-v1'
                and receipt.get('enabled') is True and receipt.get('complete') is True
                and receipt.get('capabilityAdmitted') is False and receipt.get('viewStable') is True
                and receipt.get('renderedFrameIndex')==frame,
                'baby horse preview inputs are missing or stale')
        observations=receipt.get('observations')
        require(isinstance(observations,list) and len(observations)==1,
                'baby horse preview requires one observed entity')
        row=copy.deepcopy(observations[0]);animation=row.pop('animation',None)
        require(row==expected and isinstance(animation,list) and len(animation)==4
                and type(animation[0]) in (int,float) and animation[0]>=0
                and animation[1:]==[0.0,0.0,0.0],
                'baby horse preview source inputs differ from the inspected Frozen scope')
        require(receipt.get('screenMouse')==[
            {'stage':'background','stored':[213.0,120.0],'supplied':[213.0,120.0]},
            {'stage':'render-return','stored':[213.0,120.0],'supplied':[213.0,120.0]}],
            'baby horse preview cursor inputs differ from the inspected Frozen scope')
        ages.append(animation)
    return dict(passed=True,capability_admitted=False,ordinary_non_driving_age=ages)


def paired_iron_equipped_baby_horse_preview_inputs(frozen,current,frozen_frame,current_frame):
    return paired_baby_horse_preview_inputs(frozen,current,frozen_frame,current_frame)


def paired_gold_equipped_baby_horse_preview_inputs(frozen,current,frozen_frame,current_frame):
    return paired_baby_horse_preview_inputs(frozen,current,frozen_frame,current_frame)


def paired_copper_equipped_baby_horse_preview_inputs(frozen,current,frozen_frame,current_frame):
    return paired_baby_horse_preview_inputs(frozen,current,frozen_frame,current_frame)


def paired_leather_equipped_baby_horse_preview_inputs(frozen,current,frozen_frame,current_frame):
    return paired_baby_horse_preview_inputs(frozen,current,frozen_frame,current_frame)



def paired_dyed_leather_equipped_baby_horse_preview_inputs(frozen,current,frozen_frame,current_frame):
    return paired_baby_horse_preview_inputs(frozen,current,frozen_frame,current_frame)


def paired_chested_donkey_preview_inputs(frozen,current,frozen_frame,current_frame):
    copies=[copy.deepcopy(frozen),copy.deepcopy(current)]
    for receipt in copies:
        observations=receipt.get('observations') if isinstance(receipt,dict) else None
        require(isinstance(observations,list) and len(observations)==1
                and observations[0].get('translation')==[0.0,1.0,0.0],
                'chested donkey preview translation differs from Frozen')
        observations[0]['translation']=[0.0,1.05,0.0]
    return paired_horse_preview_inputs(copies[0],copies[1],frozen_frame,current_frame)


def paired_iron_equipped_horse_preview_inputs(frozen,current,frozen_frame,current_frame):
    return paired_horse_preview_inputs(frozen,current,frozen_frame,current_frame)


def paired_gold_equipped_horse_preview_inputs(frozen,current,frozen_frame,current_frame):
    return paired_horse_preview_inputs(frozen,current,frozen_frame,current_frame)


def paired_copper_equipped_horse_preview_inputs(frozen,current,frozen_frame,current_frame):
    return paired_horse_preview_inputs(frozen,current,frozen_frame,current_frame)


def paired_leather_equipped_horse_preview_inputs(frozen,current,frozen_frame,current_frame):
    return paired_horse_preview_inputs(frozen,current,frozen_frame,current_frame)


def paired_baby_chested_donkey_preview_inputs(frozen,current,frozen_frame,current_frame):
    copies=[copy.deepcopy(frozen),copy.deepcopy(current)]
    for receipt in copies:
        observations=receipt.get('observations') if isinstance(receipt,dict) else None
        require(isinstance(observations,list) and len(observations)==1
                and observations[0].get('translation')==[0.0,0.625,0.0],
                'baby chested donkey preview translation differs from Frozen')
        observations[0]['translation']=[0.0,0.65,0.0]
    return paired_baby_horse_preview_inputs(copies[0],copies[1],frozen_frame,current_frame)


def paired_chested_mule_preview_inputs(frozen,current,frozen_frame,current_frame):
    return paired_horse_preview_inputs(frozen,current,frozen_frame,current_frame)


def paired_baby_chested_mule_preview_inputs(frozen,current,frozen_frame,current_frame):
    return paired_baby_horse_preview_inputs(frozen,current,frozen_frame,current_frame)


def paired_chested_blue_llama_preview_inputs(frozen,current,frozen_frame,current_frame):
    copies=[copy.deepcopy(frozen),copy.deepcopy(current)]
    for receipt in copies:
        observations=receipt.get('observations') if isinstance(receipt,dict) else None
        require(isinstance(observations,list) and len(observations)==1
                and observations[0].get('translation')==[0.0,1.185,0.0],
                'chested blue-carpet llama preview translation differs from Frozen')
        observations[0]['translation']=[0.0,1.05,0.0]
    return paired_horse_preview_inputs(copies[0],copies[1],frozen_frame,current_frame)


def paired_baby_chested_blue_llama_preview_inputs(frozen,current,frozen_frame,current_frame):
    copies=[copy.deepcopy(frozen),copy.deepcopy(current)]
    for receipt in copies:
        observations=receipt.get('observations') if isinstance(receipt,dict) else None
        require(isinstance(observations,list) and len(observations)==1
                and observations[0].get('translation')==[0.0,0.7175,0.0],
                'baby chested blue-carpet llama preview translation differs from Frozen')
        observations[0]['translation']=[0.0,0.65,0.0]
    return paired_baby_horse_preview_inputs(copies[0],copies[1],frozen_frame,current_frame)


def validate_horse_owner(owner, frame):
    expected = dict(same_acquired_presented_image=True, rust_whole_frame_presenter=True,
        java_vulkan_frame_execution=False, gui_entity_preview_items=1,
        gui_entity_preview_batches=1, gui_entity_preview_draws=2,
        gui_entity_preview_material_mask=128, gui_entity_preview_vertices=288,
        gui_entity_preview_indices=432)
    require(isinstance(owner, dict) and owner.get('deterministic_rendered_frame_index') == frame
            and all(owner.get(key) == value for key, value in expected.items())
            and integer(owner.get('gameplay_frame_id'), 1) and integer(owner.get('gal_submission_id'), 1)
            and owner.get('present_completed_submission_id') == owner.get('gal_submission_id'),
            'horse preview lacks exact completed Rust ownership')
    return {key: owner[key] for key in ('gameplay_frame_id', 'gal_submission_id',
        'present_completed_submission_id', *expected)}


def validate_equipped_horse_owner(owner, frame):
    expected = dict(same_acquired_presented_image=True, rust_whole_frame_presenter=True,
        java_vulkan_frame_execution=False, gui_entity_preview_items=1,
        gui_entity_preview_batches=3, gui_entity_preview_draws=4,
        gui_entity_preview_material_mask=128, gui_entity_preview_vertices=984,
        gui_entity_preview_indices=1476)
    require(isinstance(owner, dict) and owner.get('deterministic_rendered_frame_index') == frame
            and all(owner.get(key) == value for key, value in expected.items())
            and integer(owner.get('gameplay_frame_id'), 1) and integer(owner.get('gal_submission_id'), 1)
            and owner.get('present_completed_submission_id') == owner.get('gal_submission_id'),
            'equipped horse preview lacks exact completed Rust ownership')
    return {key: owner[key] for key in ('gameplay_frame_id', 'gal_submission_id',
        'present_completed_submission_id', *expected)}


def validate_black_horse_owner(owner, frame):
    return validate_horse_owner(owner, frame)


def validate_brown_horse_owner(owner, frame):
    return validate_horse_owner(owner, frame)


def validate_creamy_horse_owner(owner, frame):
    return validate_horse_owner(owner, frame)


def validate_chestnut_horse_owner(owner, frame):
    return validate_horse_owner(owner, frame)


def validate_gray_horse_owner(owner, frame):
    return validate_horse_owner(owner, frame)


def validate_dark_brown_horse_owner(owner, frame):
    return validate_horse_owner(owner, frame)


def validate_white_marking_horse_owner(owner, frame):
    return validate_marked_horse_owner(owner, frame)


def validate_white_field_horse_owner(owner, frame):
    return validate_marked_horse_owner(owner, frame)


def validate_black_dots_horse_owner(owner, frame):
    return validate_marked_horse_owner(owner, frame)


def validate_saddled_skeleton_horse_owner(owner, frame):
    expected = dict(same_acquired_presented_image=True, rust_whole_frame_presenter=True,
        java_vulkan_frame_execution=False, gui_entity_preview_items=1,
        gui_entity_preview_batches=2, gui_entity_preview_draws=3,
        gui_entity_preview_material_mask=128, gui_entity_preview_vertices=696,
        gui_entity_preview_indices=1044)
    require(isinstance(owner, dict) and owner.get('deterministic_rendered_frame_index') == frame
            and all(owner.get(key) == value for key, value in expected.items())
            and integer(owner.get('gameplay_frame_id'), 1) and integer(owner.get('gal_submission_id'), 1)
            and owner.get('present_completed_submission_id') == owner.get('gal_submission_id'),
            'saddled skeleton horse preview lacks exact completed Rust ownership')
    return {key: owner[key] for key in ('gameplay_frame_id', 'gal_submission_id',
        'present_completed_submission_id', *expected)}


def validate_saddled_baby_skeleton_horse_owner(owner, frame):
    return validate_saddled_skeleton_horse_owner(owner, frame)


def validate_saddled_zombie_horse_owner(owner, frame):
    return validate_saddled_skeleton_horse_owner(owner, frame)


def validate_saddled_baby_zombie_horse_owner(owner, frame):
    return validate_saddled_skeleton_horse_owner(owner, frame)


def validate_iron_equipped_horse_owner(owner,frame):
    return validate_equipped_horse_owner(owner,frame)


def validate_gold_equipped_horse_owner(owner,frame):
    return validate_equipped_horse_owner(owner,frame)


def validate_copper_equipped_horse_owner(owner,frame):
    return validate_equipped_horse_owner(owner,frame)


def validate_leather_equipped_horse_owner(owner,frame):
    return validate_equipped_horse_owner(owner,frame)



def validate_dyed_leather_equipped_horse_owner(owner,frame):
    return validate_equipped_horse_owner(owner,frame)


def validate_baby_horse_owner(owner, frame):
    return validate_horse_owner(owner, frame)


def paired_baby_horse_geometry(frozen,current,frozen_frame,current_frame):
    common=dict(schema='equipment-model-geometry-v1',enabled=True,complete=True,
        gpuReadback=False,capabilityAdmitted=False)
    normalized=[];ages=[]
    for side,receipt,frame in (('frozen',frozen,frozen_frame),('current',current,current_frame)):
        require(isinstance(receipt,dict)
                and all(receipt.get(key)==value for key,value in common.items())
                and receipt.get('renderedFrameIndex')==frame,
                f'{side} baby horse geometry is missing or stale')
        require(isinstance(receipt.get('models'),list) and len(receipt['models'])==1,
                f'{side} baby horse requires one base model')
        model=copy.deepcopy(receipt['models'][0]);pose=model.get('sourcePose');state=model.get('state')
        require(model.get('texture')=='minecraft:textures/entity/horse/horse_white.png'
                and model.get('pipeline')=='minecraft:pipeline/entity_cutout_no_cull'
                and model.get('tint')==-1 and model.get('light')==15728880
                and model.get('overlay')==655360
                and isinstance(model.get('quads'),list) and len(model['quads'])==72
                and isinstance(pose,dict) and len(pose.get('modelView',[]))==16
                and len(pose.get('normal',[]))==9 and isinstance(state,list) and len(state)==5
                and state[1:]==[0.5,165.3437,-14.656311,15.454812]
                and type(state[0]) in (int,float) and state[0]>=0,
                f'{side} baby horse base model differs from Frozen')
        guard=model.pop('entityPipGuardPixels',None)
        require(guard is None if side=='frozen' else guard==1,
                f'{side} baby horse entity-PIP guard differs from the measured scope')
        if guard is not None:
            pose['modelView'][12]-=guard;pose['modelView'][13]-=guard
        ages.append(state[0]);state[0]=0.0;normalized.append(model)
    require(normalized[0]==normalized[1],
            'baby horse baked model geometry differs after guard normalization')
    expected_draw=dict(texture='minecraft:textures/entity/horse/horse_white.png',
        pipeline='minecraft:pipeline/entity_cutout_no_cull',depthCompare='LEQUAL_DEPTH_TEST',
        depthWrite=True,cull=False,blend='none',vertices=288,indices=432,
        view=[1.0,0.0,0.0,0.0,
              0.0,1.0,0.0,0.0,
              0.0,0.0,1.0,0.0,
              0.0,0.0,0.0,1.0])
    require(frozen.get('completedDraws')==[expected_draw],
            'Frozen baby horse draw policy differs from the inspected reference')
    require(current.get('completedDraws')==[],
            'Current unexpectedly executed the Java baby horse draw hook')
    return dict(passed=True,capability_admitted=False,models=1,quads=72,
                current_entity_pip_guard_pixels=1,ordinary_non_driving_age=ages,
                frozen_completed_draw=expected_draw)


def paired_black_horse_geometry(frozen,current,frozen_frame,current_frame,
        texture='minecraft:textures/entity/horse/horse_black.png'):
    common=dict(schema='equipment-model-geometry-v1',enabled=True,complete=True,
        gpuReadback=False,capabilityAdmitted=False)
    normalized=[];ages=[]
    for side,receipt,frame in (('frozen',frozen,frozen_frame),('current',current,current_frame)):
        require(isinstance(receipt,dict)
                and all(receipt.get(key)==value for key,value in common.items())
                and receipt.get('renderedFrameIndex')==frame,
                f'{side} black horse geometry is missing or stale')
        require(isinstance(receipt.get('models'),list) and len(receipt['models'])==1,
                f'{side} black horse requires one base model')
        model=copy.deepcopy(receipt['models'][0]);pose=model.get('sourcePose');state=model.get('state')
        require(model.get('texture')==texture
                and model.get('pipeline')=='minecraft:pipeline/entity_cutout_no_cull'
                and model.get('tint')==-1 and model.get('light')==15728880
                and model.get('overlay')==655360
                and isinstance(model.get('quads'),list) and len(model['quads'])==72
                and isinstance(pose,dict) and len(pose.get('modelView',[]))==16
                and len(pose.get('normal',[]))==9 and isinstance(state,list) and len(state)==5
                and state[1:]==[1.0,165.3437,-14.656311,15.454812]
                and type(state[0]) in (int,float) and state[0]>=0,
                f'{side} black horse base model differs from Frozen')
        guard=model.pop('entityPipGuardPixels',None)
        require(guard is None if side=='frozen' else guard==1,
                f'{side} black horse entity-PIP guard differs from the measured scope')
        if guard is not None:
            pose['modelView'][12]-=guard;pose['modelView'][13]-=guard
        ages.append(state[0]);state[0]=0.0;normalized.append(model)
    require(normalized[0]==normalized[1],
            'black horse baked model geometry differs after guard normalization')
    expected_draw=dict(texture=texture,pipeline='minecraft:pipeline/entity_cutout_no_cull',
        depthCompare='LEQUAL_DEPTH_TEST',depthWrite=True,cull=False,blend='none',
        vertices=288,indices=432,view=[1.0,0.0,0.0,0.0,
              0.0,1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,1.0])
    require(frozen.get('completedDraws')==[expected_draw],
            'Frozen black horse draw policy differs from the inspected reference')
    require(current.get('completedDraws')==[],
            'Current unexpectedly executed the Java black horse draw hook')
    return dict(passed=True,capability_admitted=False,models=1,quads=72,
                current_entity_pip_guard_pixels=1,ordinary_non_driving_age=ages,
                frozen_completed_draw=expected_draw)


def paired_brown_horse_geometry(frozen,current,frozen_frame,current_frame):
    return paired_black_horse_geometry(frozen,current,frozen_frame,current_frame,
        texture='minecraft:textures/entity/horse/horse_brown.png')


def paired_creamy_horse_geometry(frozen,current,frozen_frame,current_frame):
    return paired_black_horse_geometry(frozen,current,frozen_frame,current_frame,
        texture='minecraft:textures/entity/horse/horse_creamy.png')


def paired_chestnut_horse_geometry(frozen,current,frozen_frame,current_frame):
    return paired_black_horse_geometry(frozen,current,frozen_frame,current_frame,
        texture='minecraft:textures/entity/horse/horse_chestnut.png')


def paired_gray_horse_geometry(frozen,current,frozen_frame,current_frame):
    return paired_black_horse_geometry(frozen,current,frozen_frame,current_frame,
        texture='minecraft:textures/entity/horse/horse_gray.png')


def paired_dark_brown_horse_geometry(frozen,current,frozen_frame,current_frame):
    return paired_black_horse_geometry(frozen,current,frozen_frame,current_frame,
        texture='minecraft:textures/entity/horse/horse_darkbrown.png')


def paired_saddled_skeleton_horse_geometry(frozen,current,frozen_frame,current_frame,baby=False,zombie=False):
    common=dict(schema='equipment-model-geometry-v1',enabled=True,complete=True,
        gpuReadback=False,capabilityAdmitted=False)
    base='minecraft:textures/entity/horse/horse_zombie.png' if zombie else 'minecraft:textures/entity/horse/horse_skeleton.png'
    saddle='minecraft:textures/entity/equipment/zombie_horse_saddle/saddle.png' if zombie else 'minecraft:textures/entity/equipment/skeleton_horse_saddle/saddle.png'
    specs=[(base,'minecraft:pipeline/entity_cutout_no_cull',72,288,432,False),
        (saddle,'minecraft:pipeline/armor_cutout_no_cull',102,408,612,True)]
    normalized=[];ages=[]
    for side,receipt,frame in (('frozen',frozen,frozen_frame),('current',current,current_frame)):
        require(isinstance(receipt,dict) and all(receipt.get(k)==v for k,v in common.items())
                and receipt.get('renderedFrameIndex')==frame and len(receipt.get('models',[]))==2,
                f'{side} saddled skeleton horse geometry is missing or stale')
        by_texture={m.get('texture'):copy.deepcopy(m) for m in receipt['models'] if isinstance(m,dict)}
        require(len(by_texture)==2,f'{side} saddled skeleton horse model identities are not unique')
        rows=[]
        for texture,pipeline,quads,_,_,_ in specs:
            model=by_texture.get(texture);pose=model.get('sourcePose') if isinstance(model,dict) else None
            state=model.get('state') if isinstance(model,dict) else None
            require(isinstance(model,dict) and model.get('pipeline')==pipeline and model.get('tint')==-1
                    and model.get('light')==15728880 and model.get('overlay')==655360
                    and len(model.get('quads',[]))==quads and isinstance(pose,dict)
                    and len(pose.get('modelView',[]))==16 and len(pose.get('normal',[]))==9
                    and isinstance(state,list) and len(state)==5
                    and state[1:]==([0.5,165.3437,-14.656311,15.454812] if baby
                        else [1.0,165.3437,-14.656311,15.454812])
                    and type(state[0]) in (int,float) and state[0]>=0,
                    f'{side} saddled skeleton horse model differs from Frozen')
            guard=model.pop('entityPipGuardPixels',None)
            require(guard is None if side=='frozen' else guard==1,
                    f'{side} saddled skeleton horse entity-PIP guard differs')
            if guard is not None:pose['modelView'][12]-=guard;pose['modelView'][13]-=guard
            ages.append(state[0]);state[0]=0.0;rows.append(model)
        normalized.append(rows)
    require(normalized[0]==normalized[1],
            'saddled skeleton horse geometry differs after guard normalization')
    identity=[1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,1.0]
    layered=list(identity);layered[14]=0.001953125
    draw_specs=list(reversed(specs)) if baby else specs
    draws=[dict(texture=t,pipeline=p,depthCompare='LEQUAL_DEPTH_TEST',depthWrite=True,cull=False,
        blend='none',vertices=v,indices=i,view=layered if offset else identity)
        for t,p,_,v,i,offset in draw_specs]
    require(frozen.get('completedDraws')==draws,'Frozen saddled skeleton horse draw policy differs')
    require(current.get('completedDraws')==[],'Current unexpectedly executed Java saddled skeleton horse draws')
    return dict(passed=True,capability_admitted=False,models=2,quads=[72,102],
        current_entity_pip_guard_pixels=1,ordinary_non_driving_age=ages,frozen_completed_draws=draws)


def paired_saddled_baby_skeleton_horse_geometry(frozen,current,frozen_frame,current_frame):
    return paired_saddled_skeleton_horse_geometry(frozen,current,frozen_frame,current_frame,baby=True)


def paired_saddled_zombie_horse_geometry(frozen,current,frozen_frame,current_frame):
    return paired_saddled_skeleton_horse_geometry(frozen,current,frozen_frame,current_frame,zombie=True)


def paired_saddled_baby_zombie_horse_geometry(frozen,current,frozen_frame,current_frame):
    return paired_saddled_skeleton_horse_geometry(frozen,current,frozen_frame,current_frame,baby=True,zombie=True)


def paired_equipped_baby_horse_geometry(frozen,current,frozen_frame,current_frame):
    common=dict(schema='equipment-model-geometry-v1',enabled=True,complete=True,
        gpuReadback=False,capabilityAdmitted=False)
    specs=[('minecraft:textures/entity/horse/horse_white.png','minecraft:pipeline/entity_cutout_no_cull',72,288,432),
        ('minecraft:textures/entity/equipment/horse_body/diamond.png','minecraft:pipeline/armor_cutout_no_cull',72,288,432),
        ('minecraft:textures/entity/equipment/horse_saddle/saddle.png','minecraft:pipeline/armor_cutout_no_cull',102,408,612)]
    normalized=[]
    for side,receipt,frame in (('frozen',frozen,frozen_frame),('current',current,current_frame)):
        require(isinstance(receipt,dict) and all(receipt.get(k)==v for k,v in common.items())
                and receipt.get('renderedFrameIndex')==frame and len(receipt.get('models',[]))==3,
                f'{side} equipped baby horse geometry is missing or stale')
        by_texture={m.get('texture'):copy.deepcopy(m) for m in receipt['models'] if isinstance(m,dict)}
        require(len(by_texture)==3,'equipped baby horse model identities are not unique')
        rows=[]
        for texture,pipeline,quads,_,_ in specs:
            model=by_texture.get(texture);pose=model.get('sourcePose') if isinstance(model,dict) else None
            state=model.get('state') if isinstance(model,dict) else None
            require(isinstance(model,dict) and model.get('pipeline')==pipeline and model.get('tint')==-1
                    and model.get('light')==15728880 and model.get('overlay')==655360
                    and len(model.get('quads',[]))==quads and isinstance(pose,dict)
                    and len(pose.get('modelView',[]))==16 and len(pose.get('normal',[]))==9
                    and isinstance(state,list) and len(state)==5
                    and state[1:]==[0.5,165.3437,-14.656311,15.454812],
                    f'{side} equipped baby horse model differs from Frozen')
            guard=model.pop('entityPipGuardPixels',None)
            require(guard is None if side=='frozen' else guard==1,'equipped baby horse PIP guard differs')
            if guard is not None:pose['modelView'][12]-=guard;pose['modelView'][13]-=guard
            state[0]=0.0;rows.append(model)
        normalized.append(rows)
    require(normalized[0]==normalized[1],'equipped baby horse geometry differs after guard normalization')
    identity_view=[1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,1.0]
    equipment_view=[*identity_view];equipment_view[14]=0.001953125
    draws=[dict(texture=t,pipeline=p,depthCompare='LEQUAL_DEPTH_TEST',depthWrite=True,cull=False,
        blend='none',vertices=v,indices=i,view=identity_view if n==0 else equipment_view)
        for n,(t,p,_,v,i) in enumerate(specs)]
    require(sorted(frozen.get('completedDraws',[]),key=lambda r:r.get('texture',''))==sorted(draws,key=lambda r:r['texture']),
            'Frozen equipped baby horse draw policy differs')
    require(current.get('completedDraws')==[],'Current unexpectedly executed Java equipped baby horse draws')
    return dict(passed=True,capability_admitted=False,models=3,quads=[72,72,102],
                current_entity_pip_guard_pixels=1,frozen_completed_draws=draws)


def paired_iron_equipped_baby_horse_geometry(frozen,current,frozen_frame,current_frame):
    copies=[copy.deepcopy(frozen),copy.deepcopy(current)]
    iron='minecraft:textures/entity/equipment/horse_body/iron.png'
    diamond='minecraft:textures/entity/equipment/horse_body/diamond.png'
    for receipt in copies:
        for model in receipt.get('models',[]) if isinstance(receipt,dict) else []:
            if model.get('texture')==iron:model['texture']=diamond
        for draw in receipt.get('completedDraws',[]) if isinstance(receipt,dict) else []:
            if draw.get('texture')==iron:draw['texture']=diamond
    result=paired_equipped_baby_horse_geometry(copies[0],copies[1],frozen_frame,current_frame)
    for draw in result['frozen_completed_draws']:
        if draw['texture']==diamond:draw['texture']=iron
    return result


def paired_gold_equipped_baby_horse_geometry(frozen,current,frozen_frame,current_frame):
    copies=[copy.deepcopy(frozen),copy.deepcopy(current)]
    gold='minecraft:textures/entity/equipment/horse_body/gold.png'
    diamond='minecraft:textures/entity/equipment/horse_body/diamond.png'
    for receipt in copies:
        for model in receipt.get('models',[]) if isinstance(receipt,dict) else []:
            if model.get('texture')==gold:model['texture']=diamond
        for draw in receipt.get('completedDraws',[]) if isinstance(receipt,dict) else []:
            if draw.get('texture')==gold:draw['texture']=diamond
    result=paired_equipped_baby_horse_geometry(copies[0],copies[1],frozen_frame,current_frame)
    for draw in result['frozen_completed_draws']:
        if draw['texture']==diamond:draw['texture']=gold
    return result


def paired_copper_equipped_baby_horse_geometry(frozen,current,frozen_frame,current_frame):
    copies=[copy.deepcopy(frozen),copy.deepcopy(current)]
    copper='minecraft:textures/entity/equipment/horse_body/copper.png'
    diamond='minecraft:textures/entity/equipment/horse_body/diamond.png'
    for receipt in copies:
        for model in receipt.get('models',[]) if isinstance(receipt,dict) else []:
            if model.get('texture')==copper:model['texture']=diamond
        for draw in receipt.get('completedDraws',[]) if isinstance(receipt,dict) else []:
            if draw.get('texture')==copper:draw['texture']=diamond
    result=paired_equipped_baby_horse_geometry(copies[0],copies[1],frozen_frame,current_frame)
    for draw in result['frozen_completed_draws']:
        if draw['texture']==diamond:draw['texture']=copper
    return result


def paired_leather_equipped_baby_horse_geometry(frozen,current,frozen_frame,current_frame):
    copies=[copy.deepcopy(frozen),copy.deepcopy(current)]
    leather='minecraft:textures/entity/equipment/horse_body/leather.png'
    diamond='minecraft:textures/entity/equipment/horse_body/diamond.png'
    for receipt in copies:
        models=receipt.get('models',[]) if isinstance(receipt,dict) else []
        leather_models=[model for model in models if model.get('texture')==leather]
        require(len(leather_models)==1 and leather_models[0].get('tint')==-6265536,
                'leather baby horse body must retain the default vanilla dye tint')
        leather_models[0]['texture']=diamond
        leather_models[0]['tint']=-1
        for draw in receipt.get('completedDraws',[]) if isinstance(receipt,dict) else []:
            if draw.get('texture')==leather:draw['texture']=diamond
    result=paired_equipped_baby_horse_geometry(copies[0],copies[1],frozen_frame,current_frame)
    for draw in result['frozen_completed_draws']:
        if draw['texture']==diamond:draw['texture']=leather
    result['leather_tint']=-6265536
    return result



def paired_dyed_leather_equipped_baby_horse_geometry(frozen,current,frozen_frame,current_frame):
    copies=[copy.deepcopy(frozen),copy.deepcopy(current)]
    leather='minecraft:textures/entity/equipment/horse_body/leather.png'
    diamond='minecraft:textures/entity/equipment/horse_body/diamond.png'
    dyed_tint=-13408564  # 0xFF3366CC
    for receipt in copies:
        models=receipt.get('models',[]) if isinstance(receipt,dict) else []
        leather_models=[model for model in models if model.get('texture')==leather]
        require(len(leather_models)==1 and leather_models[0].get('tint')==dyed_tint,
                'dyed leather baby horse body must retain the exact authored item tint')
        leather_models[0]['texture']=diamond
        leather_models[0]['tint']=-1
        for draw in receipt.get('completedDraws',[]) if isinstance(receipt,dict) else []:
            if draw.get('texture')==leather:draw['texture']=diamond
    result=paired_equipped_baby_horse_geometry(copies[0],copies[1],frozen_frame,current_frame)
    for draw in result['frozen_completed_draws']:
        if draw['texture']==diamond:draw['texture']=leather
    result['leather_tint']=dyed_tint
    result['dye_rgb']=0x3366CC
    return result


def paired_marked_horse_geometry(frozen,current,frozen_frame,current_frame,baby=False,
                                 marking_texture='minecraft:textures/entity/horse/horse_markings_whitedots.png'):
    common=dict(schema='equipment-model-geometry-v1',enabled=True,complete=True,
        gpuReadback=False,capabilityAdmitted=False)
    specs=[('minecraft:textures/entity/horse/horse_white.png','minecraft:pipeline/entity_cutout_no_cull','none'),
        (marking_texture,'minecraft:pipeline/entity_translucent',
         'BlendFunction[sourceColor=SRC_ALPHA, destColor=ONE_MINUS_SRC_ALPHA, sourceAlpha=ONE, destAlpha=ONE_MINUS_SRC_ALPHA]')]
    normalized=[];ages=[]
    for side,receipt,frame in (('frozen',frozen,frozen_frame),('current',current,current_frame)):
        require(isinstance(receipt,dict) and all(receipt.get(k)==v for k,v in common.items())
                and receipt.get('renderedFrameIndex')==frame and len(receipt.get('models',[]))==2,
                f'{side} marked horse geometry is missing or stale')
        by_texture={m.get('texture'):copy.deepcopy(m) for m in receipt['models'] if isinstance(m,dict)}
        require(len(by_texture)==2,f'{side} marked horse model identities are not unique')
        rows=[]
        for texture,pipeline,_ in specs:
            model=by_texture.get(texture);pose=model.get('sourcePose') if isinstance(model,dict) else None
            state=model.get('state') if isinstance(model,dict) else None
            require(isinstance(model,dict) and model.get('pipeline')==pipeline and model.get('tint')==-1
                    and model.get('light')==15728880 and model.get('overlay')==655360
                    and len(model.get('quads',[]))==72 and isinstance(pose,dict)
                    and len(pose.get('modelView',[]))==16 and len(pose.get('normal',[]))==9
                    and isinstance(state,list) and len(state)==5
                    and state[1:]==([0.5,165.3437,-14.656311,15.454812] if baby else [1.0,165.3437,-14.656311,15.454812])
                    and type(state[0]) in (int,float) and state[0]>=0,
                    f'{side} marked horse model differs from Frozen')
            guard=model.pop('entityPipGuardPixels',None)
            require(guard is None if side=='frozen' else guard==1,f'{side} marked horse PIP guard differs')
            if guard is not None:pose['modelView'][12]-=guard;pose['modelView'][13]-=guard
            ages.append(state[0]);state[0]=0.0;rows.append(model)
        normalized.append(rows)
    require(normalized[0]==normalized[1],'marked horse geometry differs after guard normalization')
    view=[1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,1.0]
    draws=[dict(texture=t,pipeline=p,depthCompare='LEQUAL_DEPTH_TEST',depthWrite=True,cull=False,
        blend=b,vertices=288,indices=432,view=view) for t,p,b in specs]
    require(frozen.get('completedDraws')==draws,'Frozen marked horse draw policy differs')
    require(current.get('completedDraws')==[],'Current unexpectedly executed Java marked horse draws')
    return dict(passed=True,capability_admitted=False,models=2,quads=[72,72],
                current_entity_pip_guard_pixels=1,ordinary_non_driving_age=ages,
                frozen_completed_draws=draws)


def paired_baby_marked_horse_geometry(frozen,current,frozen_frame,current_frame):
    return paired_marked_horse_geometry(frozen,current,frozen_frame,current_frame,baby=True)


def paired_white_marking_horse_geometry(frozen,current,frozen_frame,current_frame):
    return paired_marked_horse_geometry(frozen,current,frozen_frame,current_frame,
        marking_texture='minecraft:textures/entity/horse/horse_markings_white.png')


def paired_white_field_horse_geometry(frozen,current,frozen_frame,current_frame):
    return paired_marked_horse_geometry(frozen,current,frozen_frame,current_frame,
        marking_texture='minecraft:textures/entity/horse/horse_markings_whitefield.png')


def paired_black_dots_horse_geometry(frozen,current,frozen_frame,current_frame):
    return paired_marked_horse_geometry(frozen,current,frozen_frame,current_frame,
        marking_texture='minecraft:textures/entity/horse/horse_markings_blackdots.png')


def paired_equipped_marked_horse_geometry(frozen,current,frozen_frame,current_frame,baby=False):
    common=dict(schema='equipment-model-geometry-v1',enabled=True,complete=True,
        gpuReadback=False,capabilityAdmitted=False)
    specs=[
        ('minecraft:textures/entity/horse/horse_white.png','minecraft:pipeline/entity_cutout_no_cull',72,288,432,'none',False),
        ('minecraft:textures/entity/horse/horse_markings_whitedots.png','minecraft:pipeline/entity_translucent',72,288,432,
         'BlendFunction[sourceColor=SRC_ALPHA, destColor=ONE_MINUS_SRC_ALPHA, sourceAlpha=ONE, destAlpha=ONE_MINUS_SRC_ALPHA]',False)]
    equipment=[
        ('minecraft:textures/entity/equipment/horse_body/diamond.png','minecraft:pipeline/armor_cutout_no_cull',72,288,432,'none',True),
        ('minecraft:textures/entity/equipment/horse_saddle/saddle.png','minecraft:pipeline/armor_cutout_no_cull',102,408,612,'none',True)]
    specs += equipment if baby else list(reversed(equipment))
    normalized=[];ages=[]
    for side,receipt,frame in (('frozen',frozen,frozen_frame),('current',current,current_frame)):
        require(isinstance(receipt,dict) and all(receipt.get(k)==v for k,v in common.items())
                and receipt.get('renderedFrameIndex')==frame and len(receipt.get('models',[]))==4,
                f'{side} equipped marked horse geometry is missing or stale')
        by_texture={m.get('texture'):copy.deepcopy(m) for m in receipt['models'] if isinstance(m,dict)}
        require(len(by_texture)==4,f'{side} equipped marked horse model identities are not unique')
        rows=[]
        for texture,pipeline,quads,_,_,_,_ in specs:
            model=by_texture.get(texture);pose=model.get('sourcePose') if isinstance(model,dict) else None
            state=model.get('state') if isinstance(model,dict) else None
            require(isinstance(model,dict) and model.get('pipeline')==pipeline and model.get('tint')==-1
                    and model.get('light')==15728880 and model.get('overlay')==655360
                    and len(model.get('quads',[]))==quads and isinstance(pose,dict)
                    and len(pose.get('modelView',[]))==16 and len(pose.get('normal',[]))==9
                    and isinstance(state,list) and len(state)==5
                    and state[1:]==([0.5,165.3437,-14.656311,15.454812] if baby else [1.0,165.3437,-14.656311,15.454812])
                    and type(state[0]) in (int,float) and state[0]>=0,
                    f'{side} equipped marked horse model differs from Frozen')
            guard=model.pop('entityPipGuardPixels',None)
            require(guard is None if side=='frozen' else guard==1,f'{side} equipped marked horse PIP guard differs')
            if guard is not None:pose['modelView'][12]-=guard;pose['modelView'][13]-=guard
            ages.append(state[0]);state[0]=0.0;rows.append(model)
        normalized.append(rows)
    require(normalized[0]==normalized[1],'equipped marked horse geometry differs after guard normalization')
    identity=[1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,1.0]
    equipment=list(identity);equipment[14]=0.001953125
    draws=[dict(texture=t,pipeline=p,depthCompare='LEQUAL_DEPTH_TEST',depthWrite=True,cull=False,
        blend=b,vertices=v,indices=i,view=equipment if layered else identity)
        for t,p,_,v,i,b,layered in specs]
    require(frozen.get('completedDraws')==draws,'Frozen equipped marked horse draw policy differs')
    require(current.get('completedDraws')==[],'Current unexpectedly executed Java equipped marked horse draws')
    return dict(passed=True,capability_admitted=False,models=4,
        quads=[72,72,72,102] if baby else [72,72,102,72],
        current_entity_pip_guard_pixels=1,ordinary_non_driving_age=ages,frozen_completed_draws=draws)


def paired_equipped_baby_marked_horse_geometry(frozen,current,frozen_frame,current_frame):
    return paired_equipped_marked_horse_geometry(frozen,current,frozen_frame,current_frame,baby=True)


def paired_chested_donkey_geometry(frozen,current,frozen_frame,current_frame):
    common=dict(schema='equipment-model-geometry-v1',enabled=True,complete=True,
        gpuReadback=False,capabilityAdmitted=False)
    specs=[
        ('minecraft:textures/entity/equipment/donkey_saddle/saddle.png','minecraft:pipeline/armor_cutout_no_cull',114,456,684,True),
        ('minecraft:textures/entity/horse/donkey.png','minecraft:pipeline/entity_cutout_no_cull',84,336,504,False)]
    normalized=[];ages=[]
    for side,receipt,frame in (('frozen',frozen,frozen_frame),('current',current,current_frame)):
        require(isinstance(receipt,dict) and all(receipt.get(k)==v for k,v in common.items())
                and receipt.get('renderedFrameIndex')==frame and len(receipt.get('models',[]))==2,
                f'{side} chested donkey geometry is missing or stale')
        by_texture={m.get('texture'):copy.deepcopy(m) for m in receipt['models'] if isinstance(m,dict)}
        require(len(by_texture)==2,f'{side} chested donkey model identities are not unique')
        rows=[]
        for texture,pipeline,quads,_,_,_ in specs:
            model=by_texture.get(texture);pose=model.get('sourcePose') if isinstance(model,dict) else None
            state=model.get('state') if isinstance(model,dict) else None
            require(isinstance(model,dict) and model.get('pipeline')==pipeline and model.get('tint')==-1
                    and model.get('light')==15728880 and model.get('overlay')==655360
                    and len(model.get('quads',[]))==quads and isinstance(pose,dict)
                    and len(pose.get('modelView',[]))==16 and len(pose.get('normal',[]))==9
                    and isinstance(state,list) and len(state)==5
                    and state[1:]==[1.0,165.3437,-14.656311,15.454812]
                    and type(state[0]) in (int,float) and state[0]>=0,
                    f'{side} chested donkey model differs from Frozen')
            guard=model.pop('entityPipGuardPixels',None)
            require(guard is None if side=='frozen' else guard==1,
                    f'{side} chested donkey PIP guard differs from the measured scope')
            if guard is not None:pose['modelView'][12]-=guard;pose['modelView'][13]-=guard
            ages.append(state[0]);state[0]=0.0;rows.append(model)
        normalized.append(rows)
    require(normalized[0]==normalized[1],'chested donkey geometry differs after guard normalization')
    identity=[1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,1.0]
    layered=list(identity);layered[14]=0.001953125
    draws=[dict(texture=t,pipeline=p,depthCompare='LEQUAL_DEPTH_TEST',depthWrite=True,cull=False,
        blend='none',vertices=v,indices=i,view=layered if offset else identity)
        for t,p,_,v,i,offset in specs]
    require(frozen.get('completedDraws')==draws,'Frozen chested donkey draw policy differs')
    require(current.get('completedDraws')==[],'Current unexpectedly executed Java chested donkey draws')
    return dict(passed=True,capability_admitted=False,models=2,quads=[114,84],
        current_entity_pip_guard_pixels=1,ordinary_non_driving_age=ages,frozen_completed_draws=draws)


def paired_chested_mule_geometry(frozen,current,frozen_frame,current_frame):
    common=dict(schema='equipment-model-geometry-v1',enabled=True,complete=True,
        gpuReadback=False,capabilityAdmitted=False)
    specs=[
        ('minecraft:textures/entity/equipment/mule_saddle/saddle.png','minecraft:pipeline/armor_cutout_no_cull',114,456,684,True),
        ('minecraft:textures/entity/horse/mule.png','minecraft:pipeline/entity_cutout_no_cull',84,336,504,False)]
    normalized=[];ages=[]
    for side,receipt,frame in (('frozen',frozen,frozen_frame),('current',current,current_frame)):
        require(isinstance(receipt,dict) and all(receipt.get(k)==v for k,v in common.items())
                and receipt.get('renderedFrameIndex')==frame and len(receipt.get('models',[]))==2,
                f'{side} chested mule geometry is missing or stale')
        by_texture={m.get('texture'):copy.deepcopy(m) for m in receipt['models'] if isinstance(m,dict)}
        require(len(by_texture)==2,f'{side} chested mule model identities are not unique')
        rows=[]
        for texture,pipeline,quads,_,_,_ in specs:
            model=by_texture.get(texture);pose=model.get('sourcePose') if isinstance(model,dict) else None
            state=model.get('state') if isinstance(model,dict) else None
            require(isinstance(model,dict) and model.get('pipeline')==pipeline and model.get('tint')==-1
                    and model.get('light')==15728880 and model.get('overlay')==655360
                    and len(model.get('quads',[]))==quads and isinstance(pose,dict)
                    and len(pose.get('modelView',[]))==16 and len(pose.get('normal',[]))==9
                    and isinstance(state,list) and len(state)==5
                    and state[1:]==[1.0,165.3437,-14.656311,15.454812]
                    and type(state[0]) in (int,float) and state[0]>=0,
                    f'{side} chested mule model differs from Frozen')
            guard=model.pop('entityPipGuardPixels',None)
            require(guard is None if side=='frozen' else guard==1,
                    f'{side} chested mule PIP guard differs from the measured scope')
            if guard is not None:pose['modelView'][12]-=guard;pose['modelView'][13]-=guard
            ages.append(state[0]);state[0]=0.0;rows.append(model)
        normalized.append(rows)
    require(normalized[0]==normalized[1],'chested mule geometry differs after guard normalization')
    identity=[1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,1.0]
    layered=list(identity);layered[14]=0.001953125
    draws=[dict(texture=t,pipeline=p,depthCompare='LEQUAL_DEPTH_TEST',depthWrite=True,cull=False,
        blend='none',vertices=v,indices=i,view=layered if offset else identity)
        for t,p,_,v,i,offset in specs]
    require(frozen.get('completedDraws')==draws,'Frozen chested mule draw policy differs')
    require(current.get('completedDraws')==[],'Current unexpectedly executed Java chested mule draws')
    return dict(passed=True,capability_admitted=False,models=2,quads=[114,84],
        current_entity_pip_guard_pixels=1,ordinary_non_driving_age=ages,frozen_completed_draws=draws)


def paired_baby_chested_mule_geometry(frozen,current,frozen_frame,current_frame):
    common=dict(schema='equipment-model-geometry-v1',enabled=True,complete=True,
        gpuReadback=False,capabilityAdmitted=False)
    specs=[
        ('minecraft:textures/entity/horse/mule.png','minecraft:pipeline/entity_cutout_no_cull',84,336,504,False),
        ('minecraft:textures/entity/equipment/mule_saddle/saddle.png','minecraft:pipeline/armor_cutout_no_cull',114,456,684,True)]
    normalized=[];ages=[]
    for side,receipt,frame in (('frozen',frozen,frozen_frame),('current',current,current_frame)):
        require(isinstance(receipt,dict) and all(receipt.get(k)==v for k,v in common.items())
                and receipt.get('renderedFrameIndex')==frame and len(receipt.get('models',[]))==2,
                f'{side} baby chested mule geometry is missing or stale')
        by_texture={m.get('texture'):copy.deepcopy(m) for m in receipt['models'] if isinstance(m,dict)}
        require(len(by_texture)==2,f'{side} baby chested mule model identities are not unique')
        rows=[];tail_quads=[]
        for texture,pipeline,quads,_,_,_ in specs:
            model=by_texture.get(texture);pose=model.get('sourcePose') if isinstance(model,dict) else None
            state=model.get('state') if isinstance(model,dict) else None
            require(isinstance(model,dict) and model.get('pipeline')==pipeline and model.get('tint')==-1
                    and model.get('light')==15728880 and model.get('overlay')==655360
                    and len(model.get('quads',[]))==quads and isinstance(pose,dict)
                    and len(pose.get('modelView',[]))==16 and len(pose.get('normal',[]))==9
                    and isinstance(state,list) and len(state)==5
                    and state[1:]==[0.5,165.3437,-14.656311,15.454812]
                    and type(state[0]) in (int,float) and state[0]>=0,
                    f'{side} baby chested mule model differs from Frozen')
            guard=model.pop('entityPipGuardPixels',None)
            require(guard is None if side=='frozen' else guard==1,
                    f'{side} baby chested mule PIP guard differs from the measured scope')
            if guard is not None:pose['modelView'][12]-=guard;pose['modelView'][13]-=guard
            tail=[q for q in model['quads'] if q.get('part')=='/body/tail']
            require(len(tail)==6,f'{side} baby chested mule tail geometry is incomplete')
            tail_quads.append(tail);model['quads']=[q for q in model['quads'] if q.get('part')!='/body/tail']
            ages.append(state[0]);state[0]=0.0;rows.append(model)
        require(tail_quads[0]==tail_quads[1],f'{side} baby chested mule base/saddle tail poses differ')
        normalized.append(rows)
    require(normalized[0]==normalized[1],'baby chested mule geometry differs after guard normalization')
    identity=[1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,1.0]
    layered=list(identity);layered[14]=0.001953125
    draws=[dict(texture=t,pipeline=p,depthCompare='LEQUAL_DEPTH_TEST',depthWrite=True,cull=False,
        blend='none',vertices=v,indices=i,view=layered if offset else identity)
        for t,p,_,v,i,offset in specs]
    require(frozen.get('completedDraws')==draws,'Frozen baby chested mule draw policy differs')
    require(current.get('completedDraws')==[],'Current unexpectedly executed Java baby chested mule draws')
    return dict(passed=True,capability_admitted=False,models=2,quads=[84,114],age_driven_tail_quads=6,
        current_entity_pip_guard_pixels=1,ordinary_idle_age=ages,frozen_completed_draws=draws)


def paired_baby_chested_donkey_geometry(frozen,current,frozen_frame,current_frame):
    copies=[copy.deepcopy(frozen),copy.deepcopy(current)]
    donkey_base='minecraft:textures/entity/horse/donkey.png'
    mule_base='minecraft:textures/entity/horse/mule.png'
    donkey_saddle='minecraft:textures/entity/equipment/donkey_saddle/saddle.png'
    mule_saddle='minecraft:textures/entity/equipment/mule_saddle/saddle.png'
    for receipt in copies:
        for model in receipt.get('models',[]) if isinstance(receipt,dict) else []:
            if model.get('texture')==donkey_base:model['texture']=mule_base
            elif model.get('texture')==donkey_saddle:model['texture']=mule_saddle
        for draw in receipt.get('completedDraws',[]) if isinstance(receipt,dict) else []:
            if draw.get('texture')==donkey_base:draw['texture']=mule_base
            elif draw.get('texture')==donkey_saddle:draw['texture']=mule_saddle
    result=paired_baby_chested_mule_geometry(copies[0],copies[1],frozen_frame,current_frame)
    for draw in result['frozen_completed_draws']:
        if draw['texture']==mule_base:draw['texture']=donkey_base
        elif draw['texture']==mule_saddle:draw['texture']=donkey_saddle
    return result


def paired_chested_blue_llama_geometry(frozen,current,frozen_frame,current_frame):
    common=dict(schema='equipment-model-geometry-v1',enabled=True,complete=True,
        gpuReadback=False,capabilityAdmitted=False)
    specs=[
        ('minecraft:textures/entity/llama/creamy.png','minecraft:pipeline/entity_cutout_no_cull',False),
        ('minecraft:textures/entity/equipment/llama_body/blue.png','minecraft:pipeline/armor_cutout_no_cull',True)]
    normalized=[];ages=[]
    for side,receipt,frame in (('frozen',frozen,frozen_frame),('current',current,current_frame)):
        require(isinstance(receipt,dict) and all(receipt.get(k)==v for k,v in common.items())
                and receipt.get('renderedFrameIndex')==frame and len(receipt.get('models',[]))==2,
                f'{side} chested blue-carpet llama geometry is missing or stale')
        by_texture={m.get('texture'):copy.deepcopy(m) for m in receipt['models'] if isinstance(m,dict)}
        require(len(by_texture)==2,f'{side} chested blue-carpet llama model identities are not unique')
        rows=[]
        for texture,pipeline,_ in specs:
            model=by_texture.get(texture);pose=model.get('sourcePose') if isinstance(model,dict) else None
            state=model.get('state') if isinstance(model,dict) else None
            require(isinstance(model,dict) and model.get('pipeline')==pipeline and model.get('tint')==-1
                    and model.get('light')==15728880 and model.get('overlay')==655360
                    and len(model.get('quads',[]))==66 and isinstance(pose,dict)
                    and len(pose.get('modelView',[]))==16 and len(pose.get('normal',[]))==9
                    and isinstance(state,list) and len(state)==5
                    and state[1:]==[1.0,165.3437,-14.656311,15.454812]
                    and type(state[0]) in (int,float) and state[0]>=0,
                    f'{side} chested blue-carpet llama model differs from Frozen')
            guard=model.pop('entityPipGuardPixels',None)
            require(guard is None if side=='frozen' else guard==1,
                    f'{side} chested blue-carpet llama PIP guard differs from the measured scope')
            if guard is not None:pose['modelView'][12]-=guard;pose['modelView'][13]-=guard
            ages.append(state[0]);state[0]=0.0;rows.append(model)
        normalized.append(rows)
    require(normalized[0]==normalized[1],
            'chested blue-carpet llama geometry differs after guard normalization')
    identity=[1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,1.0]
    layered=list(identity);layered[14]=0.001953125
    draws=[dict(texture=t,pipeline=p,depthCompare='LEQUAL_DEPTH_TEST',depthWrite=True,cull=False,
        blend='none',vertices=264,indices=396,view=layered if offset else identity)
        for t,p,offset in specs]
    require(frozen.get('completedDraws')==draws,'Frozen chested blue-carpet llama draw policy differs')
    require(current.get('completedDraws')==[],'Current unexpectedly executed Java chested llama draws')
    return dict(passed=True,capability_admitted=False,models=2,quads=[66,66],
        current_entity_pip_guard_pixels=1,ordinary_non_driving_age=ages,frozen_completed_draws=draws)


def paired_baby_chested_blue_llama_geometry(frozen,current,frozen_frame,current_frame):
    common=dict(schema='equipment-model-geometry-v1',enabled=True,complete=True,
        gpuReadback=False,capabilityAdmitted=False)
    specs=[
        ('minecraft:textures/entity/llama/creamy.png','minecraft:pipeline/entity_cutout_no_cull',False),
        ('minecraft:textures/entity/equipment/llama_body/blue.png','minecraft:pipeline/armor_cutout_no_cull',True)]
    normalized=[];ages=[]
    for side,receipt,frame in (('frozen',frozen,frozen_frame),('current',current,current_frame)):
        require(isinstance(receipt,dict) and all(receipt.get(k)==v for k,v in common.items())
                and receipt.get('renderedFrameIndex')==frame and len(receipt.get('models',[]))==2,
                f'{side} baby chested blue-carpet llama geometry is missing or stale')
        by_texture={m.get('texture'):copy.deepcopy(m) for m in receipt['models'] if isinstance(m,dict)}
        require(len(by_texture)==2,f'{side} baby chested blue-carpet llama model identities are not unique')
        rows=[]
        for texture,pipeline,_ in specs:
            model=by_texture.get(texture);pose=model.get('sourcePose') if isinstance(model,dict) else None
            state=model.get('state') if isinstance(model,dict) else None
            require(isinstance(model,dict) and model.get('pipeline')==pipeline and model.get('tint')==-1
                    and model.get('light')==15728880 and model.get('overlay')==655360
                    and len(model.get('quads',[]))==54 and isinstance(pose,dict)
                    and len(pose.get('modelView',[]))==16 and len(pose.get('normal',[]))==9
                    and isinstance(state,list) and len(state)==5
                    and state[1:]==[0.5,165.3437,-14.656311,15.454812]
                    and type(state[0]) in (int,float) and state[0]>=0,
                    f'{side} baby chested blue-carpet llama model differs from Frozen')
            guard=model.pop('entityPipGuardPixels',None)
            require(guard is None if side=='frozen' else guard==1,
                    f'{side} baby chested blue-carpet llama PIP guard differs from the measured scope')
            if guard is not None:pose['modelView'][12]-=guard;pose['modelView'][13]-=guard
            ages.append(state[0]);state[0]=0.0;rows.append(model)
        normalized.append(rows)
    require(normalized[0]==normalized[1],
            'baby chested blue-carpet llama geometry differs after guard normalization')
    identity=[1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,1.0]
    layered=list(identity);layered[14]=0.001953125
    draws=[dict(texture=t,pipeline=p,depthCompare='LEQUAL_DEPTH_TEST',depthWrite=True,cull=False,
        blend='none',vertices=216,indices=324,view=layered if offset else identity)
        for t,p,offset in specs]
    require(frozen.get('completedDraws')==draws,'Frozen baby chested blue-carpet llama draw policy differs')
    require(current.get('completedDraws')==[],'Current unexpectedly executed Java baby chested llama draws')
    return dict(passed=True,capability_admitted=False,models=2,quads=[54,54],
        current_entity_pip_guard_pixels=1,ordinary_non_driving_age=ages,frozen_completed_draws=draws)


def paired_equipped_horse_geometry(frozen, current, frozen_frame, current_frame):
    common = dict(schema='equipment-model-geometry-v1', enabled=True, complete=True,
        gpuReadback=False, capabilityAdmitted=False)
    expected = [
        ('minecraft:textures/entity/equipment/horse_body/diamond.png', 72, 288, 432),
        ('minecraft:textures/entity/equipment/horse_saddle/saddle.png', 102, 408, 612)]
    normalized=[]
    for side,receipt,frame in (('frozen',frozen,frozen_frame),('current',current,current_frame)):
        require(isinstance(receipt,dict)
                and all(receipt.get(key)==value for key,value in common.items())
                and receipt.get('renderedFrameIndex')==frame,
                f'{side} equipped horse geometry is missing or stale')
        require(isinstance(receipt.get('models'),list) and len(receipt['models'])==2,
                f'{side} equipped horse requires armor and saddle models')
        rows=[]
        models_by_texture={model.get('texture'):model for model in receipt['models'] if isinstance(model,dict)}
        require(len(models_by_texture)==2, f'{side} equipped horse model identities are not unique')
        for texture,quads,_,_ in expected:
            model=models_by_texture.get(texture)
            require(isinstance(model,dict), f'{side} equipped horse model is missing {texture}')
            row=copy.deepcopy(model);guard=row.pop('entityPipGuardPixels',None)
            require((guard is None if side=='frozen' else guard==1)
                    and row.get('texture')==texture
                    and row.get('pipeline')=='minecraft:pipeline/armor_cutout_no_cull'
                    and row.get('tint')==-1 and row.get('light')==15728880
                    and row.get('overlay')==655360
                    and isinstance(row.get('quads'),list) and len(row['quads'])==quads,
                    f'{side} equipped horse model differs from Frozen')
            rows.append(row)
        normalized.append(rows)
    require(normalized[0]==normalized[1], 'equipped horse armor or saddle geometry differs from Frozen')
    view=[1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.001953125,1.0]
    draws=[]
    for texture,_,vertices,indices in expected:
        draws.append(dict(texture=texture,pipeline='minecraft:pipeline/armor_cutout_no_cull',
            depthCompare='LEQUAL_DEPTH_TEST',depthWrite=True,cull=False,blend='none',
            vertices=vertices,indices=indices,view=view))
    frozen_draws=frozen.get('completedDraws')
    require(isinstance(frozen_draws,list) and len(frozen_draws)==2
            and sorted(frozen_draws,key=lambda row:row.get('texture',''))
                == sorted(draws,key=lambda row:row.get('texture','')),
            'Frozen equipped horse draw policy differs from the inspected reference')
    require(current.get('completedDraws')==[],
            'Current unexpectedly executed Java equipped horse draws')
    return dict(passed=True,capability_admitted=False,models=2,quads=[72,102],
                current_entity_pip_guard_pixels=1,frozen_completed_draws=draws)


def paired_iron_equipped_horse_geometry(frozen,current,frozen_frame,current_frame):
    copies=[copy.deepcopy(frozen),copy.deepcopy(current)]
    iron='minecraft:textures/entity/equipment/horse_body/iron.png'
    diamond='minecraft:textures/entity/equipment/horse_body/diamond.png'
    for receipt in copies:
        for model in receipt.get('models',[]) if isinstance(receipt,dict) else []:
            if model.get('texture')==iron:model['texture']=diamond
        for draw in receipt.get('completedDraws',[]) if isinstance(receipt,dict) else []:
            if draw.get('texture')==iron:draw['texture']=diamond
    result=paired_equipped_horse_geometry(copies[0],copies[1],frozen_frame,current_frame)
    for draw in result['frozen_completed_draws']:
        if draw['texture']==diamond:draw['texture']=iron
    return result


def paired_gold_equipped_horse_geometry(frozen,current,frozen_frame,current_frame):
    copies=[copy.deepcopy(frozen),copy.deepcopy(current)]
    gold='minecraft:textures/entity/equipment/horse_body/gold.png'
    diamond='minecraft:textures/entity/equipment/horse_body/diamond.png'
    for receipt in copies:
        for model in receipt.get('models',[]) if isinstance(receipt,dict) else []:
            if model.get('texture')==gold:model['texture']=diamond
        for draw in receipt.get('completedDraws',[]) if isinstance(receipt,dict) else []:
            if draw.get('texture')==gold:draw['texture']=diamond
    result=paired_equipped_horse_geometry(copies[0],copies[1],frozen_frame,current_frame)
    for draw in result['frozen_completed_draws']:
        if draw['texture']==diamond:draw['texture']=gold
    return result


def paired_copper_equipped_horse_geometry(frozen,current,frozen_frame,current_frame):
    copies=[copy.deepcopy(frozen),copy.deepcopy(current)]
    copper='minecraft:textures/entity/equipment/horse_body/copper.png'
    diamond='minecraft:textures/entity/equipment/horse_body/diamond.png'
    for receipt in copies:
        for model in receipt.get('models',[]) if isinstance(receipt,dict) else []:
            if model.get('texture')==copper:model['texture']=diamond
        for draw in receipt.get('completedDraws',[]) if isinstance(receipt,dict) else []:
            if draw.get('texture')==copper:draw['texture']=diamond
    result=paired_equipped_horse_geometry(copies[0],copies[1],frozen_frame,current_frame)
    for draw in result['frozen_completed_draws']:
        if draw['texture']==diamond:draw['texture']=copper
    return result


def paired_leather_equipped_horse_geometry(frozen,current,frozen_frame,current_frame):
    copies=[copy.deepcopy(frozen),copy.deepcopy(current)]
    leather='minecraft:textures/entity/equipment/horse_body/leather.png'
    diamond='minecraft:textures/entity/equipment/horse_body/diamond.png'
    for receipt in copies:
        models=receipt.get('models',[]) if isinstance(receipt,dict) else []
        leather_models=[model for model in models if model.get('texture')==leather]
        require(len(leather_models)==1 and leather_models[0].get('tint')==-6265536,
                'leather horse body must retain the default vanilla dye tint')
        leather_models[0]['texture']=diamond
        leather_models[0]['tint']=-1
        for draw in receipt.get('completedDraws',[]) if isinstance(receipt,dict) else []:
            if draw.get('texture')==leather:draw['texture']=diamond
    result=paired_equipped_horse_geometry(copies[0],copies[1],frozen_frame,current_frame)
    for draw in result['frozen_completed_draws']:
        if draw['texture']==diamond:draw['texture']=leather
    result['leather_tint']=-6265536
    return result



def paired_dyed_leather_equipped_horse_geometry(frozen,current,frozen_frame,current_frame):
    copies=[copy.deepcopy(frozen),copy.deepcopy(current)]
    leather='minecraft:textures/entity/equipment/horse_body/leather.png'
    diamond='minecraft:textures/entity/equipment/horse_body/diamond.png'
    dyed_tint=-13408564  # 0xFF3366CC
    for receipt in copies:
        models=receipt.get('models',[]) if isinstance(receipt,dict) else []
        leather_models=[model for model in models if model.get('texture')==leather]
        require(len(leather_models)==1 and leather_models[0].get('tint')==dyed_tint,
                'dyed leather horse body must retain the exact authored item tint')
        leather_models[0]['texture']=diamond
        leather_models[0]['tint']=-1
        for draw in receipt.get('completedDraws',[]) if isinstance(receipt,dict) else []:
            if draw.get('texture')==leather:draw['texture']=diamond
    result=paired_equipped_horse_geometry(copies[0],copies[1],frozen_frame,current_frame)
    for draw in result['frozen_completed_draws']:
        if draw['texture']==diamond:draw['texture']=leather
    result['leather_tint']=dyed_tint
    result['dye_rgb']=0x3366CC
    return result


def compare_horse_images(frozen, current):
    require(frozen.size == current.size == (1280, 720), 'horse preview requires 1280x720 images')
    difference = ImageChops.difference(frozen.convert('RGB').crop(HORSE_CROP),
                                       current.convert('RGB').crop(HORSE_CROP))
    stats = ImageStat.Stat(difference)
    pixels = list(difference.get_flattened_data())
    result = dict(box=list(HORSE_CROP), mean_rgb_abs=stats.mean,
        mean_absolute_rgb=sum(stats.mean) / 3, rms_rgb_abs=stats.rms,
        max_channel_abs=max(max(pixel) for pixel in pixels),
        nonzero_pixels=sum(pixel != (0, 0, 0) for pixel in pixels), pixel_count=len(pixels))
    require(result['mean_absolute_rgb'] <= 2.0 and result['max_channel_abs'] <= 4,
            'horse preview pixels differ from Frozen')
    return dict(passed=True, capability_admitted=False, **result)


def compare_horse_paths(frozen, current):
    with Image.open(frozen) as baseline, Image.open(current) as candidate:
        return compare_horse_images(baseline, candidate)


def compare_black_horse_images(frozen, current):
    return compare_horse_images(frozen, current)


def compare_black_horse_paths(frozen, current):
    return compare_horse_paths(frozen, current)


def compare_brown_horse_paths(frozen, current):
    return compare_horse_paths(frozen, current)


def compare_creamy_horse_paths(frozen, current):
    return compare_horse_paths(frozen, current)


def compare_chestnut_horse_paths(frozen, current):
    return compare_horse_paths(frozen, current)


def compare_gray_horse_paths(frozen, current):
    return compare_horse_paths(frozen, current)


def compare_dark_brown_horse_paths(frozen, current):
    return compare_horse_paths(frozen, current)


def compare_white_marking_horse_paths(frozen, current):
    return compare_marked_horse_paths(frozen, current)


def compare_white_field_horse_paths(frozen, current):
    return compare_marked_horse_paths(frozen, current)


def compare_black_dots_horse_paths(frozen, current):
    return compare_marked_horse_paths(frozen, current)


def compare_saddled_skeleton_horse_images(frozen, current):
    require(frozen.size == current.size == (1280, 720),
            'saddled skeleton horse preview requires 1280x720 images')
    difference=ImageChops.difference(frozen.convert('RGB').crop(HORSE_CROP),
                                     current.convert('RGB').crop(HORSE_CROP))
    stats=ImageStat.Stat(difference);pixels=list(difference.get_flattened_data())
    maxima=sorted(max(pixel) for pixel in pixels)
    result=dict(box=list(HORSE_CROP),mean_rgb_abs=stats.mean,
        mean_absolute_rgb=sum(stats.mean)/3,rms_rgb_abs=stats.rms,
        max_channel_abs=max(maxima),p99_channel_abs=maxima[int(len(maxima)*0.99)],
        high_delta_pixels=sum(value>64 for value in maxima),
        nonzero_pixels=sum(pixel!=(0,0,0) for pixel in pixels),pixel_count=len(pixels))
    require(result['mean_absolute_rgb']<=1.25 and result['max_channel_abs']<=160
            and result['p99_channel_abs']<=24 and result['high_delta_pixels']<=160
            and result['nonzero_pixels']<=3200,
            'saddled skeleton horse preview pixels differ from Frozen')
    return dict(passed=True,capability_admitted=False,**result)


def compare_saddled_skeleton_horse_paths(frozen, current):
    with Image.open(frozen) as baseline, Image.open(current) as candidate:
        return compare_saddled_skeleton_horse_images(baseline,candidate)


def compare_saddled_baby_skeleton_horse_images(frozen, current):
    return compare_saddled_skeleton_horse_images(frozen, current)


def compare_saddled_baby_skeleton_horse_paths(frozen, current):
    return compare_saddled_skeleton_horse_paths(frozen, current)


def compare_saddled_zombie_horse_paths(frozen, current):
    with Image.open(frozen) as baseline, Image.open(current) as candidate:
        require(baseline.size == candidate.size == (1280, 720),
                'saddled zombie horse preview requires 1280x720 images')
        difference=ImageChops.difference(baseline.convert('RGB').crop(HORSE_CROP),
                                         candidate.convert('RGB').crop(HORSE_CROP))
        stats=ImageStat.Stat(difference);pixels=list(difference.get_flattened_data())
        maxima=sorted(max(pixel) for pixel in pixels)
        result=dict(box=list(HORSE_CROP),mean_rgb_abs=stats.mean,
            mean_absolute_rgb=sum(stats.mean)/3,rms_rgb_abs=stats.rms,
            max_channel_abs=max(maxima),p99_channel_abs=maxima[int(len(maxima)*0.99)],
            high_delta_pixels=sum(value>64 for value in maxima),
            nonzero_pixels=sum(pixel!=(0,0,0) for pixel in pixels),pixel_count=len(pixels))
        require(result['mean_absolute_rgb']<=0.25 and result['max_channel_abs']<=64
                and result['p99_channel_abs']<=3 and result['high_delta_pixels']==0
                and result['nonzero_pixels']<=3600,
                'saddled zombie horse preview pixels differ from Frozen')
        return dict(passed=True,capability_admitted=False,**result)


def compare_saddled_baby_zombie_horse_paths(frozen, current):
    return compare_saddled_zombie_horse_paths(frozen, current)


def compare_equipped_horse_images(frozen, current):
    require(frozen.size == current.size == (1280, 720),
            'equipped horse preview requires 1280x720 images')
    difference=ImageChops.difference(frozen.convert('RGB').crop(HORSE_CROP),
                                     current.convert('RGB').crop(HORSE_CROP))
    stats=ImageStat.Stat(difference);pixels=list(difference.get_flattened_data())
    result=dict(box=list(HORSE_CROP),mean_rgb_abs=stats.mean,
        mean_absolute_rgb=sum(stats.mean)/3,rms_rgb_abs=stats.rms,
        max_channel_abs=max(max(pixel) for pixel in pixels),
        nonzero_pixels=sum(pixel!=(0,0,0) for pixel in pixels),pixel_count=len(pixels))
    require(result['mean_absolute_rgb']<=1.0 and result['max_channel_abs']<=80,
            'equipped horse preview pixels differ from Frozen')
    return dict(passed=True,capability_admitted=False,**result)


def compare_equipped_horse_paths(frozen,current):
    with Image.open(frozen) as baseline,Image.open(current) as candidate:
        return compare_equipped_horse_images(baseline,candidate)


def compare_iron_equipped_horse_images(frozen,current):
    return compare_equipped_horse_images(frozen,current)


def compare_iron_equipped_horse_paths(frozen,current):
    with Image.open(frozen) as baseline,Image.open(current) as candidate:
        return compare_iron_equipped_horse_images(baseline,candidate)


def compare_gold_equipped_horse_images(frozen,current):
    return compare_equipped_horse_images(frozen,current)


def compare_gold_equipped_horse_paths(frozen,current):
    with Image.open(frozen) as baseline,Image.open(current) as candidate:
        return compare_gold_equipped_horse_images(baseline,candidate)


def compare_copper_equipped_horse_images(frozen,current):
    return compare_equipped_horse_images(frozen,current)


def compare_copper_equipped_horse_paths(frozen,current):
    with Image.open(frozen) as baseline,Image.open(current) as candidate:
        return compare_copper_equipped_horse_images(baseline,candidate)


def compare_leather_equipped_horse_images(frozen,current):
    return compare_equipped_horse_images(frozen,current)


def compare_leather_equipped_horse_paths(frozen,current):
    with Image.open(frozen) as baseline,Image.open(current) as candidate:
        return compare_leather_equipped_horse_images(baseline,candidate)



def compare_dyed_leather_equipped_horse_images(frozen,current):
    return compare_equipped_horse_images(frozen,current)


def compare_dyed_leather_equipped_horse_paths(frozen,current):
    with Image.open(frozen) as baseline,Image.open(current) as candidate:
        return compare_dyed_leather_equipped_horse_images(baseline,candidate)


def compare_baby_horse_images(frozen,current):
    require(frozen.size==current.size==(1280,720),'baby horse preview requires 1280x720 images')
    difference=ImageChops.difference(frozen.convert('RGB').crop(HORSE_CROP),
                                     current.convert('RGB').crop(HORSE_CROP))
    stats=ImageStat.Stat(difference);pixels=list(difference.get_flattened_data())
    result=dict(box=list(HORSE_CROP),mean_rgb_abs=stats.mean,
        mean_absolute_rgb=sum(stats.mean)/3,rms_rgb_abs=stats.rms,
        max_channel_abs=max(max(pixel) for pixel in pixels),
        nonzero_pixels=sum(pixel!=(0,0,0) for pixel in pixels),pixel_count=len(pixels))
    require(result['mean_absolute_rgb']<=0.25 and result['max_channel_abs']<=4,
            'baby horse preview pixels differ from Frozen')
    return dict(passed=True,capability_admitted=False,**result)


def compare_baby_horse_paths(frozen,current):
    with Image.open(frozen) as baseline,Image.open(current) as candidate:
        return compare_baby_horse_images(baseline,candidate)


def compare_equipped_baby_horse_images(frozen,current):
    require(frozen.size==current.size==(1280,720),'equipped baby horse preview requires 1280x720 images')
    difference=ImageChops.difference(frozen.convert('RGB').crop(HORSE_CROP),current.convert('RGB').crop(HORSE_CROP))
    stats=ImageStat.Stat(difference);pixels=list(difference.get_flattened_data())
    result=dict(box=list(HORSE_CROP),mean_rgb_abs=stats.mean,mean_absolute_rgb=sum(stats.mean)/3,
        rms_rgb_abs=stats.rms,max_channel_abs=max(max(pixel) for pixel in pixels),
        nonzero_pixels=sum(pixel!=(0,0,0) for pixel in pixels),pixel_count=len(pixels))
    require(result['mean_absolute_rgb']<=0.25 and result['max_channel_abs']<=32,
            'equipped baby horse preview pixels differ from Frozen')
    return dict(passed=True,capability_admitted=False,**result)


def compare_equipped_baby_horse_paths(frozen,current):
    with Image.open(frozen) as baseline,Image.open(current) as candidate:
        return compare_equipped_baby_horse_images(baseline,candidate)


def validate_iron_equipped_baby_horse_owner(owner,frame):
    return validate_equipped_horse_owner(owner,frame)


def validate_gold_equipped_baby_horse_owner(owner,frame):
    return validate_equipped_horse_owner(owner,frame)


def validate_copper_equipped_baby_horse_owner(owner,frame):
    return validate_equipped_horse_owner(owner,frame)


def validate_leather_equipped_baby_horse_owner(owner,frame):
    return validate_equipped_horse_owner(owner,frame)



def validate_dyed_leather_equipped_baby_horse_owner(owner,frame):
    return validate_equipped_horse_owner(owner,frame)


def validate_marked_horse_owner(owner,frame):
    expected=dict(same_acquired_presented_image=True,rust_whole_frame_presenter=True,
        java_vulkan_frame_execution=False,gui_entity_preview_items=1,
        gui_entity_preview_batches=2,gui_entity_preview_draws=3,
        gui_entity_preview_material_mask=384,gui_entity_preview_vertices=576,
        gui_entity_preview_indices=864)
    require(isinstance(owner,dict) and owner.get('deterministic_rendered_frame_index')==frame
            and all(owner.get(k)==v for k,v in expected.items())
            and integer(owner.get('gameplay_frame_id'),1) and integer(owner.get('gal_submission_id'),1)
            and owner.get('present_completed_submission_id')==owner.get('gal_submission_id'),
            'marked horse preview lacks exact completed Rust ownership')
    return {key:owner[key] for key in ('gameplay_frame_id','gal_submission_id',
        'present_completed_submission_id',*expected)}


def validate_equipped_marked_horse_owner(owner,frame):
    expected=dict(same_acquired_presented_image=True,rust_whole_frame_presenter=True,
        java_vulkan_frame_execution=False,gui_entity_preview_items=1,
        gui_entity_preview_batches=4,gui_entity_preview_draws=5,
        gui_entity_preview_material_mask=384,gui_entity_preview_vertices=1272,
        gui_entity_preview_indices=1908)
    require(isinstance(owner,dict) and owner.get('deterministic_rendered_frame_index')==frame
            and all(owner.get(k)==v for k,v in expected.items())
            and integer(owner.get('gameplay_frame_id'),1) and integer(owner.get('gal_submission_id'),1)
            and owner.get('present_completed_submission_id')==owner.get('gal_submission_id'),
            'equipped marked horse preview lacks exact completed Rust ownership')
    return {key:owner[key] for key in ('gameplay_frame_id','gal_submission_id',
        'present_completed_submission_id',*expected)}


def validate_equipped_baby_marked_horse_owner(owner,frame):
    return validate_equipped_marked_horse_owner(owner,frame)


def validate_chested_donkey_owner(owner,frame):
    expected=dict(same_acquired_presented_image=True,rust_whole_frame_presenter=True,
        java_vulkan_frame_execution=False,gui_entity_preview_items=1,
        gui_entity_preview_batches=2,gui_entity_preview_draws=3,
        gui_entity_preview_material_mask=128,gui_entity_preview_vertices=792,
        gui_entity_preview_indices=1188)
    require(isinstance(owner,dict) and owner.get('deterministic_rendered_frame_index')==frame
            and all(owner.get(k)==v for k,v in expected.items())
            and integer(owner.get('gameplay_frame_id'),1) and integer(owner.get('gal_submission_id'),1)
            and owner.get('present_completed_submission_id')==owner.get('gal_submission_id'),
            'chested donkey preview lacks exact completed Rust ownership')
    return {key:owner[key] for key in ('gameplay_frame_id','gal_submission_id',
        'present_completed_submission_id',*expected)}


def validate_baby_chested_donkey_owner(owner,frame):
    return validate_chested_donkey_owner(owner,frame)


def validate_chested_mule_owner(owner,frame):
    return validate_chested_donkey_owner(owner,frame)


def validate_baby_chested_mule_owner(owner,frame):
    return validate_chested_mule_owner(owner,frame)


def validate_chested_blue_llama_owner(owner,frame):
    expected=dict(same_acquired_presented_image=True,rust_whole_frame_presenter=True,
        java_vulkan_frame_execution=False,gui_entity_preview_items=1,
        gui_entity_preview_batches=2,gui_entity_preview_draws=3,
        gui_entity_preview_material_mask=128,gui_entity_preview_vertices=528,
        gui_entity_preview_indices=792)
    require(isinstance(owner,dict) and owner.get('deterministic_rendered_frame_index')==frame
            and all(owner.get(k)==v for k,v in expected.items())
            and integer(owner.get('gameplay_frame_id'),1) and integer(owner.get('gal_submission_id'),1)
            and owner.get('present_completed_submission_id')==owner.get('gal_submission_id'),
            'chested blue-carpet llama preview lacks exact completed Rust ownership')
    return {key:owner[key] for key in ('gameplay_frame_id','gal_submission_id',
        'present_completed_submission_id',*expected)}


def validate_baby_chested_blue_llama_owner(owner,frame):
    expected=dict(same_acquired_presented_image=True,rust_whole_frame_presenter=True,
        java_vulkan_frame_execution=False,gui_entity_preview_items=1,
        gui_entity_preview_batches=2,gui_entity_preview_draws=3,
        gui_entity_preview_material_mask=128,gui_entity_preview_vertices=432,
        gui_entity_preview_indices=648)
    require(isinstance(owner,dict) and owner.get('deterministic_rendered_frame_index')==frame
            and all(owner.get(k)==v for k,v in expected.items())
            and integer(owner.get('gameplay_frame_id'),1) and integer(owner.get('gal_submission_id'),1)
            and owner.get('present_completed_submission_id')==owner.get('gal_submission_id'),
            'baby chested blue-carpet llama preview lacks exact completed Rust ownership')
    return {key:owner[key] for key in ('gameplay_frame_id','gal_submission_id',
        'present_completed_submission_id',*expected)}


def compare_iron_equipped_baby_horse_images(frozen,current):
    return compare_equipped_baby_horse_images(frozen,current)


def compare_iron_equipped_baby_horse_paths(frozen,current):
    with Image.open(frozen) as baseline,Image.open(current) as candidate:
        return compare_iron_equipped_baby_horse_images(baseline,candidate)


def compare_gold_equipped_baby_horse_images(frozen,current):
    return compare_equipped_baby_horse_images(frozen,current)


def compare_gold_equipped_baby_horse_paths(frozen,current):
    with Image.open(frozen) as baseline,Image.open(current) as candidate:
        return compare_gold_equipped_baby_horse_images(baseline,candidate)


def compare_copper_equipped_baby_horse_images(frozen,current):
    return compare_equipped_baby_horse_images(frozen,current)


def compare_copper_equipped_baby_horse_paths(frozen,current):
    with Image.open(frozen) as baseline,Image.open(current) as candidate:
        return compare_copper_equipped_baby_horse_images(baseline,candidate)


def compare_leather_equipped_baby_horse_images(frozen,current):
    return compare_equipped_baby_horse_images(frozen,current)


def compare_leather_equipped_baby_horse_paths(frozen,current):
    with Image.open(frozen) as baseline,Image.open(current) as candidate:
        return compare_leather_equipped_baby_horse_images(baseline,candidate)



def compare_dyed_leather_equipped_baby_horse_images(frozen,current):
    return compare_equipped_baby_horse_images(frozen,current)


def compare_dyed_leather_equipped_baby_horse_paths(frozen,current):
    with Image.open(frozen) as baseline,Image.open(current) as candidate:
        return compare_dyed_leather_equipped_baby_horse_images(baseline,candidate)


def compare_marked_horse_images(frozen,current):
    require(frozen.size==current.size==(1280,720),'marked horse preview requires 1280x720 images')
    difference=ImageChops.difference(frozen.convert('RGB').crop(HORSE_CROP),current.convert('RGB').crop(HORSE_CROP))
    stats=ImageStat.Stat(difference);pixels=list(difference.get_flattened_data())
    result=dict(box=list(HORSE_CROP),mean_rgb_abs=stats.mean,mean_absolute_rgb=sum(stats.mean)/3,
        rms_rgb_abs=stats.rms,max_channel_abs=max(max(pixel) for pixel in pixels),
        nonzero_pixels=sum(pixel!=(0,0,0) for pixel in pixels),pixel_count=len(pixels))
    require(result['mean_absolute_rgb']<=0.5 and result['max_channel_abs']<=4,
            'marked horse preview pixels differ from Frozen')
    return dict(passed=True,capability_admitted=False,**result)


def compare_marked_horse_paths(frozen,current):
    with Image.open(frozen) as baseline,Image.open(current) as candidate:
        return compare_marked_horse_images(baseline,candidate)


def compare_baby_marked_horse_images(frozen,current):
    result=compare_marked_horse_images(frozen,current)
    require(result['mean_absolute_rgb']<=0.25,'marked baby horse preview pixels differ from Frozen')
    return result


def compare_baby_marked_horse_paths(frozen,current):
    with Image.open(frozen) as baseline,Image.open(current) as candidate:
        return compare_baby_marked_horse_images(baseline,candidate)


def compare_equipped_baby_marked_horse_images(frozen,current):
    return compare_equipped_baby_horse_images(frozen,current)


def compare_equipped_baby_marked_horse_paths(frozen,current):
    with Image.open(frozen) as baseline,Image.open(current) as candidate:
        return compare_equipped_baby_marked_horse_images(baseline,candidate)


def compare_chested_donkey_images(frozen,current):
    require(frozen.size==current.size==(1280,720),'chested donkey preview requires 1280x720 images')
    difference=ImageChops.difference(frozen.convert('RGB').crop(HORSE_CROP),current.convert('RGB').crop(HORSE_CROP))
    stats=ImageStat.Stat(difference);pixels=list(difference.get_flattened_data())
    result=dict(box=list(HORSE_CROP),mean_rgb_abs=stats.mean,mean_absolute_rgb=sum(stats.mean)/3,
        rms_rgb_abs=stats.rms,max_channel_abs=max(max(pixel) for pixel in pixels),
        nonzero_pixels=sum(pixel!=(0,0,0) for pixel in pixels),pixel_count=len(pixels))
    require(result['mean_absolute_rgb']<=0.25 and result['max_channel_abs']<=16,
            'chested donkey preview pixels differ from Frozen')
    return dict(passed=True,capability_admitted=False,**result)


def compare_chested_donkey_paths(frozen,current):
    with Image.open(frozen) as baseline,Image.open(current) as candidate:
        return compare_chested_donkey_images(baseline,candidate)


def compare_baby_chested_donkey_images(frozen,current):
    return compare_chested_donkey_images(frozen,current)


def compare_baby_chested_donkey_paths(frozen,current):
    with Image.open(frozen) as baseline,Image.open(current) as candidate:
        return compare_baby_chested_donkey_images(baseline,candidate)


def compare_chested_mule_images(frozen,current):
    require(frozen.size==current.size==(1280,720),'chested mule preview requires 1280x720 images')
    difference=ImageChops.difference(frozen.convert('RGB').crop(HORSE_CROP),current.convert('RGB').crop(HORSE_CROP))
    stats=ImageStat.Stat(difference);pixels=list(difference.get_flattened_data())
    result=dict(box=list(HORSE_CROP),mean_rgb_abs=stats.mean,mean_absolute_rgb=sum(stats.mean)/3,
        rms_rgb_abs=stats.rms,max_channel_abs=max(max(pixel) for pixel in pixels),
        nonzero_pixels=sum(pixel!=(0,0,0) for pixel in pixels),pixel_count=len(pixels))
    require(result['mean_absolute_rgb']<=0.25 and result['max_channel_abs']<=80,
            'chested mule preview pixels differ from Frozen')
    return dict(passed=True,capability_admitted=False,**result)


def compare_chested_mule_paths(frozen,current):
    with Image.open(frozen) as baseline,Image.open(current) as candidate:
        return compare_chested_mule_images(baseline,candidate)


def compare_baby_chested_mule_images(frozen,current):
    return compare_chested_mule_images(frozen,current)


def compare_baby_chested_mule_paths(frozen,current):
    with Image.open(frozen) as baseline,Image.open(current) as candidate:
        return compare_baby_chested_mule_images(baseline,candidate)


def compare_chested_blue_llama_images(frozen,current):
    require(frozen.size==current.size==(1280,720),'chested blue-carpet llama preview requires 1280x720 images')
    difference=ImageChops.difference(frozen.convert('RGB').crop(HORSE_CROP),current.convert('RGB').crop(HORSE_CROP))
    stats=ImageStat.Stat(difference);pixels=list(difference.get_flattened_data())
    result=dict(box=list(HORSE_CROP),mean_rgb_abs=stats.mean,mean_absolute_rgb=sum(stats.mean)/3,
        rms_rgb_abs=stats.rms,max_channel_abs=max(max(pixel) for pixel in pixels),
        nonzero_pixels=sum(pixel!=(0,0,0) for pixel in pixels),pixel_count=len(pixels))
    require(result['mean_absolute_rgb']<=0.5 and result['max_channel_abs']<=64,
            'chested blue-carpet llama preview pixels differ from Frozen')
    return dict(passed=True,capability_admitted=False,**result)


def compare_chested_blue_llama_paths(frozen,current):
    with Image.open(frozen) as baseline,Image.open(current) as candidate:
        return compare_chested_blue_llama_images(baseline,candidate)


def compare_baby_chested_blue_llama_images(frozen,current):
    result=compare_chested_blue_llama_images(frozen,current)
    require(result['mean_absolute_rgb']<=0.25,
            'baby chested blue-carpet llama preview pixels differ from Frozen')
    return result


def compare_baby_chested_blue_llama_paths(frozen,current):
    with Image.open(frozen) as baseline,Image.open(current) as candidate:
        return compare_baby_chested_blue_llama_images(baseline,candidate)


def paired_smithing_preview_inputs(frozen, current, frozen_frame, current_frame):
    expected = dict(bounds=[246.0, 57.0, 286.0, 117.0], translation=[0.0, 1.0, 0.0],
        rotation=[0.0, -0.21643962, 0.976296, -0.0], scales=[25.0, 1.0, 1.0],
        invisible=False, invisibleToPlayer=False, pose='STANDING', light=15728880)
    expected_stand = dict(marker=False, small=False, showArms=True, showBasePlate=False,
        head=[0.0, 0.0, 0.0], body=[0.0, 0.0, 0.0],
        leftArm=[-10.0, 0.0, -10.0], rightArm=[-15.0, 0.0, 10.0],
        leftLeg=[-1.0, 0.0, -1.0], rightLeg=[1.0, 0.0, 1.0])
    normalized = []
    incidental = []
    for receipt, frame in ((frozen, frozen_frame), (current, current_frame)):
        require(isinstance(receipt, dict) and receipt.get('schema') == 'inventory-preview-inputs-v1'
                and receipt.get('enabled') is True and receipt.get('complete') is True
                and receipt.get('capabilityAdmitted') is False and receipt.get('viewStable') is True
                and receipt.get('renderedFrameIndex') == frame,
                'smithing preview inputs are missing or stale')
        observations = receipt.get('observations')
        require(isinstance(observations, list) and len(observations) == 1,
                'smithing preview requires one observed entity')
        row = copy.deepcopy(observations[0])
        animation = row.pop('animation', None)
        angles = row.pop('angles', None)
        stand = row.pop('armorStand', None)
        require(row == expected and isinstance(animation, list) and len(animation) == 4
                and type(animation[0]) in (int, float) and animation[0] >= 0
                and animation[1:] == [0.0, 0.0, 0.0]
                and isinstance(angles, list) and len(angles) == 3
                and angles[0] == -150.0 and angles[2] == 25.0
                and type(angles[1]) in (int, float)
                and isinstance(stand, dict) and type(stand.get('wiggle')) in (int, float)
                and stand['wiggle'] >= 5.0,
                'smithing preview source inputs differ from the inspected Frozen scope')
        wiggle = stand.pop('wiggle')
        require(stand == expected_stand,
                'smithing armor-stand flags or authored poses differ from Frozen')
        require(receipt.get('screenMouse') == [
            {'stage':'background','stored':[213.0,120.0],'supplied':[213.0,120.0]},
            {'stage':'render-return','stored':[213.0,120.0],'supplied':[213.0,120.0]}],
            'smithing preview cursor inputs differ from the inspected Frozen scope')
        normalized.append(dict(**row, angles=[angles[0], angles[2]], armorStand=stand))
        incidental.append(dict(animation=animation, hidden_base_plate_yaw=angles[1], wiggle=wiggle))
    require(normalized[0] == normalized[1], 'smithing preview driving inputs differ')
    return dict(passed=True, capability_admitted=False,
        non_driving_hidden_base_plate_and_inactive_wiggle=incidental)


def validate_smithing_owner(owner, frame):
    expected = dict(same_acquired_presented_image=True, rust_whole_frame_presenter=True,
        java_vulkan_frame_execution=False, gui_entity_preview_items=1,
        gui_entity_preview_batches=1, gui_entity_preview_draws=2,
        gui_entity_preview_material_mask=128, gui_entity_preview_vertices=216,
        gui_entity_preview_indices=324)
    require(isinstance(owner, dict) and owner.get('deterministic_rendered_frame_index') == frame
            and all(owner.get(key) == value for key, value in expected.items())
            and integer(owner.get('gameplay_frame_id'), 1) and integer(owner.get('gal_submission_id'), 1)
            and owner.get('present_completed_submission_id') == owner.get('gal_submission_id'),
            'smithing preview lacks exact completed Rust ownership')
    return {key: owner[key] for key in ('gameplay_frame_id', 'gal_submission_id',
        'present_completed_submission_id', *expected)}


def validate_equipped_smithing_owner(owner, frame):
    expected = dict(same_acquired_presented_image=True, rust_whole_frame_presenter=True,
        java_vulkan_frame_execution=False, gui_entity_preview_items=1,
        gui_entity_preview_batches=2, gui_entity_preview_draws=3,
        gui_entity_preview_material_mask=128, gui_entity_preview_vertices=288,
        gui_entity_preview_indices=432)
    require(isinstance(owner, dict) and owner.get('deterministic_rendered_frame_index') == frame
            and all(owner.get(key) == value for key, value in expected.items())
            and integer(owner.get('gameplay_frame_id'), 1) and integer(owner.get('gal_submission_id'), 1)
            and owner.get('present_completed_submission_id') == owner.get('gal_submission_id'),
            'equipped smithing preview lacks exact completed Rust ownership')
    return {key: owner[key] for key in ('gameplay_frame_id', 'gal_submission_id',
        'present_completed_submission_id', *expected)}


def paired_equipped_smithing_geometry(frozen, current, frozen_frame, current_frame):
    common = dict(schema='equipment-model-geometry-v1', enabled=True, complete=True,
        gpuReadback=False, capabilityAdmitted=False)
    normalized = []
    incidental = []
    for side, receipt, frame in (('frozen', frozen, frozen_frame),
                                  ('current', current, current_frame)):
        require(isinstance(receipt, dict)
                and all(receipt.get(key) == value for key, value in common.items())
                and receipt.get('renderedFrameIndex') == frame,
                f'{side} equipped smithing geometry is missing or stale')
        models = receipt.get('models')
        require(isinstance(models, list) and len(models) == 1,
                f'{side} equipped smithing geometry requires one armor model')
        model = copy.deepcopy(models[0])
        require(model.get('texture') == 'minecraft:textures/entity/equipment/humanoid/netherite.png'
                and model.get('pipeline') == 'minecraft:pipeline/armor_cutout_no_cull'
                and model.get('tint') == -1 and model.get('light') == 15728880
                and model.get('overlay') == 655360
                and isinstance(model.get('quads'), list) and len(model['quads']) == 18,
                f'{side} equipped smithing armor material or mesh differs from Frozen')
        pose = model.get('sourcePose')
        state = model.get('state')
        require(isinstance(pose, dict) and isinstance(pose.get('modelView'), list)
                and len(pose['modelView']) == 16 and isinstance(pose.get('normal'), list)
                and len(pose['normal']) == 9 and isinstance(state, list) and len(state) == 5
                and state[0:3] == [1.0, 1.0, -150.0] and state[4] == 25.0
                and type(state[3]) in (int, float),
                f'{side} equipped smithing pose is malformed')
        guard = model.pop('entityPipGuardPixels', None)
        if side == 'frozen':
            require(guard is None, 'Frozen unexpectedly reports a padded entity-PIP target')
        else:
            require(guard == 1, 'Current equipped smithing geometry requires the measured one-pixel entity-PIP guard')
            pose['modelView'][12] -= guard
            pose['modelView'][13] -= guard
        hidden_yaw = state[3]
        state[3] = 0.0
        normalized.append(model)
        incidental.append(dict(side=side, hidden_base_plate_yaw=hidden_yaw,
                               entity_pip_guard_pixels=guard))
    require(normalized[0] == normalized[1],
            'equipped smithing armor geometry differs after the measured entity-PIP guard normalization')
    expected_draw = dict(
        texture='minecraft:textures/entity/equipment/humanoid/netherite.png',
        pipeline='minecraft:pipeline/armor_cutout_no_cull', depthCompare='LEQUAL_DEPTH_TEST',
        depthWrite=True, cull=False, blend='none', vertices=72, indices=108,
        view=[1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,1.0,0.0,
              0.0,0.0,0.001953125,1.0])
    require(frozen.get('completedDraws') == [expected_draw],
            'Frozen equipped smithing draw policy differs from the inspected reference')
    require(current.get('completedDraws') == [],
            'Current unexpectedly executed the Java equipped smithing draw hook')
    return dict(passed=True, capability_admitted=False, models=1, quads=18,
                frozen_completed_draw=expected_draw, normalization=incidental)


def compare_smithing_images(frozen, current):
    require(frozen.size == current.size == (1280, 720), 'smithing preview requires 1280x720 images')
    difference = ImageChops.difference(frozen.convert('RGB').crop(SMITHING_CROP),
                                       current.convert('RGB').crop(SMITHING_CROP))
    stats = ImageStat.Stat(difference)
    pixels = list(difference.get_flattened_data())
    result = dict(box=list(SMITHING_CROP), mean_rgb_abs=stats.mean,
        mean_absolute_rgb=sum(stats.mean) / 3, rms_rgb_abs=stats.rms,
        max_channel_abs=max(max(pixel) for pixel in pixels),
        nonzero_pixels=sum(pixel != (0, 0, 0) for pixel in pixels), pixel_count=len(pixels))
    require(result['mean_absolute_rgb'] <= 1.0 and result['max_channel_abs'] <= 4,
            'smithing preview pixels differ from Frozen')
    return dict(passed=True, capability_admitted=False, **result)


def compare_smithing_paths(frozen, current):
    with Image.open(frozen) as baseline, Image.open(current) as candidate:
        return compare_smithing_images(baseline, candidate)


def compare_equipped_smithing_images(frozen, current):
    require(frozen.size == current.size == (1280, 720),
            'equipped smithing preview requires 1280x720 images')
    difference = ImageChops.difference(frozen.convert('RGB').crop(SMITHING_CROP),
                                       current.convert('RGB').crop(SMITHING_CROP))
    stats = ImageStat.Stat(difference)
    pixels = list(difference.get_flattened_data())
    result = dict(box=list(SMITHING_CROP), mean_rgb_abs=stats.mean,
        mean_absolute_rgb=sum(stats.mean) / 3, rms_rgb_abs=stats.rms,
        max_channel_abs=max(max(pixel) for pixel in pixels),
        nonzero_pixels=sum(pixel != (0, 0, 0) for pixel in pixels), pixel_count=len(pixels))
    require(result['mean_absolute_rgb'] <= 1.0 and result['max_channel_abs'] <= 16,
            'equipped smithing preview pixels differ from Frozen')
    return dict(passed=True, capability_admitted=False, **result)


def compare_equipped_smithing_paths(frozen, current):
    with Image.open(frozen) as baseline, Image.open(current) as candidate:
        return compare_equipped_smithing_images(baseline, candidate)
