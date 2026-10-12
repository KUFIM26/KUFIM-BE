# -*- coding: utf-8 -*-
"""
MAP-006 어파인 변환 검증.
기준점 3쌍으로 (lat,lng) -> (x,y) 변환 행렬을 실제로 풀고,
1) 행렬이 성립하는지 (특이행렬 아닌지)
2) 왕복 오차가 0인지
3) 도출된 축척이 물리적으로 타당한지 (m/px)
를 확인한다.
"""
import sys, io, sqlite3, math
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
import os as _os
_HERE = _os.path.dirname(_os.path.abspath(__file__))
_REPO = _os.path.abspath(_os.path.join(_HERE, '..', '..'))
_DOCS = _os.path.join(_REPO, 'docs')
_TMP  = _os.path.join(_HERE, '.tmp')
_os.makedirs(_TMP, exist_ok=True)


DB = _os.path.join(_TMP, 'kufim_check.db')
con = sqlite3.connect(DB); con.row_factory = sqlite3.Row
pts = con.execute("SELECT lat,lng,x,y FROM map_calibration_point ORDER BY display_order").fetchall()
fp = con.execute("SELECT width,height,image_url FROM map_floorplan").fetchone()

print('기준점 3쌍')
for p in pts:
    print('  (%.7f, %.7f) -> (%d, %d)' % (p['lat'], p['lng'], p['x'], p['y']))
print('도면 크기: %d x %d px' % (fp['width'], fp['height']))

def solve3(rows, rhs):
    """3x3 가우스 소거."""
    m = [list(rows[i]) + [rhs[i]] for i in range(3)]
    for i in range(3):
        piv = max(range(i, 3), key=lambda r: abs(m[r][i]))
        if abs(m[piv][i]) < 1e-15:
            return None
        m[i], m[piv] = m[piv], m[i]
        for r in range(3):
            if r == i: continue
            f = m[r][i] / m[i][i]
            for c in range(i, 4):
                m[r][c] -= f * m[i][c]
    return [m[i][3] / m[i][i] for i in range(3)]

# x = a*lng + b*lat + c   /   y = d*lng + e*lat + f
A = [[p['lng'], p['lat'], 1.0] for p in pts]
det = (A[0][0]*(A[1][1]*A[2][2]-A[1][2]*A[2][1])
     - A[0][1]*(A[1][0]*A[2][2]-A[1][2]*A[2][0])
     + A[0][2]*(A[1][0]*A[2][1]-A[1][1]*A[2][0]))
print('\n계수행렬 행렬식 = %.12e' % det)
if abs(det) < 1e-12:
    print('[FAIL] 특이행렬 -> M002 (일직선)'); sys.exit(1)
print('[ok]   비특이 행렬. 변환 성립')

cx = solve3(A, [float(p['x']) for p in pts])
cy = solve3(A, [float(p['y']) for p in pts])
print('\nx = %.4f*lng + %.4f*lat + %.4f' % tuple(cx))
print('y = %.4f*lng + %.4f*lat + %.4f' % tuple(cy))

def to_img(lat, lng):
    return (cx[0]*lng + cx[1]*lat + cx[2], cy[0]*lng + cy[1]*lat + cy[2])

print('\n왕복 검증 (기준점 재투영)')
maxerr = 0.0
for p in pts:
    X, Y = to_img(p['lat'], p['lng'])
    e = math.hypot(X - p['x'], Y - p['y'])
    maxerr = max(maxerr, e)
    print('  (%d,%d) -> (%.4f,%.4f)  오차 %.2e px' % (p['x'], p['y'], X, Y, e))
print('[%s]   최대 재투영 오차 %.2e px' % ('ok' if maxerr < 1e-6 else 'FAIL', maxerr))

# 축척 타당성: 위도 1도 ~ 111,320m, 경도 1도 ~ 111,320*cos(lat)
lat0 = sum(p['lat'] for p in pts) / 3
m_per_deg_lat = 111320.0
m_per_deg_lng = 111320.0 * math.cos(math.radians(lat0))

# 도면 1px 가 몇 m 인지: 역변환 야코비안
detJ = cx[0]*cy[1] - cx[1]*cy[0]           # px^2 per deg^2
# lng,lat 방향 미터 환산
px_per_m_x = math.hypot(cx[0]/m_per_deg_lng, cx[1]/m_per_deg_lat)
px_per_m_y = math.hypot(cy[0]/m_per_deg_lng, cy[1]/m_per_deg_lat)
print('\n축척 타당성')
print('  기준 위도 %.5f' % lat0)
print('  가로 %.3f px/m  (= %.3f m/px)' % (px_per_m_x, 1/px_per_m_x if px_per_m_x else 0))
print('  세로 %.3f px/m  (= %.3f m/px)' % (px_per_m_y, 1/px_per_m_y if px_per_m_y else 0))
w_m = fp['width'] / px_per_m_x
h_m = fp['height'] / px_per_m_y
print('  -> 도면이 표현하는 실제 범위: 약 %.0f m x %.0f m' % (w_m, h_m))

ok = True
if not (100 <= w_m <= 2000 and 100 <= h_m <= 2000):
    print('  [WARN] 캠퍼스 규모로는 비현실적인 축척. 기준점 좌표 재측정 필요')
    ok = False
else:
    print('  [ok]   대학 캠퍼스 규모로 타당한 범위')

# 종횡비 왜곡 검사
ratio = px_per_m_x / px_per_m_y
print('  가로/세로 축척비 = %.3f' % ratio)
if not (0.5 <= ratio <= 2.0):
    print('  [WARN] 축척 왜곡이 큼. 도면이 실제 비율과 다르거나 기준점 오차')
    ok = False
else:
    print('  [ok]   축척 왜곡 허용 범위')

print('\n결론: %s' % ('어파인 변환 설계 유효' if ok and maxerr < 1e-6 else '검토 필요 (시드 좌표는 개발용 예시값)'))
