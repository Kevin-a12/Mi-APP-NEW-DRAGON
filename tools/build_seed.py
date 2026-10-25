"""Transcripción del menú proporcionado. Importación inicial, nunca sobrescribe ediciones."""
import json
from pathlib import Path

products = []
def group(title, options, count=1):
    return dict(title=title, options=options, count=count)
def add(category, name, prices, description='', options=None):
    if isinstance(prices, int): prices = [('Normal', prices)]
    pid = f'p{len(products)+1:03}'
    products.append(dict(id=pid, category=category, name=name, description=description,
        options=options or [], sortOrder=len(products), variants=[
            dict(id=f'{pid}v{i+1}', label=label, priceCents=price*100, sortOrder=i)
            for i, (label, price) in enumerate(prices)]))
def pair(a, b): return [('Orden', a), ('Media orden', b)]

guisos = {
 'Pollo agridulce':'Pollo empanizado con gravy a la piña.',
 'Pollo enchilado':'Pollo empanizado con rodajas de jalapeño, cebolla en tiras y salsa especial.',
 'Pollo New Dragón':'Pollo empanizado con gravy especial de la casa.',
 'Pollo en tiras':'Pollo sazonado y empanizado en panko.',
 'Combinación china':'Pollo, carne, carnitas rojas y vegetales en salsa de ostión.',
 'Puerco Taipéi':'Puerco con chile serrano, cebolla y brócoli en gravy cantonés.',
 'Pollo en salsa de ostión':'Pollo asado con cebolla y salsa de ostión.',
 'Pollo tamarindo':'Pollo empanizado con gravy de tamarindo.',
 'Pollo vietnamita':'Pollo capeado con vegetales en salsa vietnamita.',
 'Pollo pekinés':'Pollo capeado con vegetales y chile de árbol, en salsa pekinés.',
 'Puerco cantonés':'Puerco con vegetales en gravy cantonés.'}
for n, price in [(1,110),(2,120),(3,180)]:
    add('Combos', f'Arroz + {n} guiso'+('s' if n>1 else ''), price,
        'Arroz con guisos a elegir.'+(' Plato 9×9.' if n==3 else ''), [group('Guiso',list(guisos),n)])
add('Combos','Mini combo (1 guiso)',90,'Plato 6×6. Arroz con un guiso a elegir.',[group('Guiso',list(guisos))])
for n,p in [('Rollos de Philadelphia',30),('Rollos de PH con camarón',35),('Dedos de queso',15)]:
    add('Entradas',n,[('Pieza',p)])
for n,a,b in [('Chiles rellenos',85,55),('Toritos',130,80),('Carnitas rojas',150,120),('Noodles',85,65),('Alitas en salsa de ostión',170,140)]: add('Entradas',n,pair(a,b))
for n,a,b in [('Pollo y vegetales',100,75),('Fan see con pollo y vegetales',110,85),('Camarón y vegetales',130,100),('Fan see con camarón y vegetales',140,110),('Ajo con pollo y vegetales',120,90),('Enchilosa',125,95)]: add('Sopas',n,[('Litro',a),('Medio litro',b)])
for n,a,b in [('de pollo',170,140),('de puerco',170,140),('de res',185,150),('de camarón',190,170),('mixto',180,150),('especial',190,160),('a la profe',190,165),('cantonés',195,165)]: add('Arroz','Arroz '+n,pair(a,b))
for n,a,b in [('de pollo',210,170),('de puerco',215,175),('de res',215,175),('de camarón',220,180),('mixto',215,175),('especial',220,180)]: add('Chow meen','Chow meen '+n,pair(a,b))
specials = [('Combinación china',220,180),('Pollo Naka',220,180),('Pollo en salsa de ostión',210,170),('Pollo agridulce',210,170),('Pollo tamarindo',210,170),('Pollo en tiras',210,170),('Pollo New Dragón',210,170),('Pollo enchilado',210,170),('Pollo vietnamita',210,170),('Pollo pekinés',210,170),('Pollo almendrado',210,170),('Pollo 5',210,170),('Pollo Mongolia',210,170),('Res con brócoli',215,175),('Puerco Taipéi',215,175),('Puerco cantonés',215,175),('Pollo con papas',210,170),('Pollo a la naranja',210,170),('Pollo kumpao',210,170),('Alitas con vegetales',210,170),('Nido de pájaro',220,180)]
for n,a,b in specials: add('Especialidades',n,pair(a,b),(guisos.get(n,'')+' Guiso sin arroz. Orden: plato 9×9. Media orden: plato 7×7.').strip())
for n,p in [('Arrochito de pollo',100),('Arrochito de res',110),('Arrochito de camarón',120),('Arrochito especial',120),('Arrochiboneless',120)]: add('Arrochitos',n,p,'Arroz frito con aderezo especial, aguacate, Philadelphia, ensalada de surimi y ajonjolí.')
for n,p in [('pollo',145),('res',150),('camarón',160),('especial',160)]: add('Yakimeshi','Yakimeshi '+n,p)
for flavor in ['BBQ','Búfalo','New Dragón','Tamarindo']: add('Boneless / Alitas',flavor,[('Boneless',130),('Alitas',130)])
add('Extras','Té',[('Litro',30),('Medio litro',20)])
add('Extras','Arroz frito',50)
add('Extras','Salsas',5,'Limón, chile de árbol o soya.',[group('Salsa',['Limón','Chile de árbol','Soya'])])
add('Extras','Aderezos',10,'Sriracha, anguila, ranch o chipotle.',[group('Aderezo',['Sriracha','Anguila','Ranch','Chipotle'])])
for n,p in [('Queso gratinado',20),('Tampico',20),('Papas fritas',40)]: add('Extras',n,p)
for n,p in [('Bombazo',100),('Pollo',100),('Res',110),('Camarón',120),('Cielo, mar y tierra',120),('Sushi boneless',145),('Tiritas roll',120)]: add('Sushi empanizado',n,p)
for n,p in [('California roll',90),('Aguacate roll',120),('Philadelphia roll',110)]: add('Sushi natural',n,p)
for n,p in [('pollo',80),('surimi',75),('res',85),('camarón',90),('especial',90)]: add('Sushibolas','Sushibola '+n,p)
add('Charolas','Normal',300,'Bombazo, sushi de pollo, sushibola especial y 2 medios litros de té.')
add('Charolas','Alucín',310,'2 sushi boneless, papas y 1 litro de té.')
add('Charolas','La People',360,'Sushi de res, sushi de camarón, 4 chiles rellenos de pollo o camarón, papas fritas y 2 litros de té.',[group('Relleno de chiles',['Pollo','Camarón'])])
add('Charolas','Deluxe',550,'Sushi de pollo, sushi de res, sushi de camarón, yakimeshi de pollo, boneless y 1 litro de té.')
add('Charolas','Goyo',450,'3 sushis a elegir (Bombazo, Pollo o California), 1 orden de boneless y papas.',[group('Sushi',['Bombazo','Pollo','California'],3)])
add('Charolas','Natural',390,'2 California roll, Aguacate roll y Philadelphia roll.')
add('Promociones','Dos mejor que uno',200,'Dos arrochitos de pollo y 1 litro de té gratis.')
add('Promociones','Combo X2',220,'Dos combos #1 y 1 litro de té gratis.',[group('Guiso del combo',list(guisos),2)])
add('Promociones','Pa mi solito',160,'Medio Bombazo, media orden de boneless, papas fritas y medio litro de té gratis.')
add('Promociones','Rompeladompe',145,'Orden de boneless, papas fritas y medio litro de té gratis.')
add('Promociones','Combo Dragón',150,'Bombazo y medio yakimeshi de pollo en grevi a elegir. Indicar grevi en observaciones.')
add('Promociones','Esferas del Dragón',230,'2 sushibolas especiales, 2 dedos de queso y 1 litro de té gratis.')
add('Promociones','Todo bienn',330,'Arrochito especial, sushibola especial, sushi cielo, mar y tierra y 1.5 litros de té gratis.')

assert len(products)==101
assert sum(len(p['variants']) for p in products)==152
assert len({p['category'] for p in products})==15
target = Path(__file__).resolve().parents[1]/'app/src/main/assets/menu_seed.json'
target.write_text(json.dumps(products,ensure_ascii=False,indent=2)+'\n')
print(f'{len(products)} platillos / 152 presentaciones / 15 categorías')
