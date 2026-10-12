"""Rebuild src/main/resources/seed/products.csv with detailed specs (EZBuild catalogue v2).

Run:  python tools/enrich_catalog.py
Keeps id/category/brand/name/price/discount/stock/price_status from the current CSV,
adds detailed specs, recomputes perf scores and writes richer summaries.
Benchmark numbers are approximate public figures (Cinebench R23 for CPUs, relative
1080p raster index for GPUs) used only to rank parts against each other.
"""
import csv, io, pathlib, sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
CSV = ROOT / "src/main/resources/seed/products.csv"

NEW = [  # prices checked on StarTech BD, 11 Oct 2026
    ["cpu-17","CPU","AMD","AMD Ryzen 5 7500F",17140,0,15,"verified-2026",0,"",""],
    ["cpu-18","CPU","Intel","Intel Core i5-14400F",20000,0,15,"verified-2026",0,"",""],
    ["cpu-19","CPU","AMD","AMD Ryzen 5 9600X",26000,0,10,"verified-2026",0,"",""],
    ["cpu-20","CPU","AMD","AMD Ryzen 5 7600X",20500,0,10,"verified-2026",0,"",""],
]

# id: socket, cores, threads, base, boost, l3, tdp, igpu, ram, arch, year, pcie, boxcooler, st, mt, gaming, note
CPU = {
 "cpu-01":("AM4",6,12,3.6,4.2,16,65,"none","DDR4","Zen 3",2022,"3.0",True,1450,10500,55,"budget 1080p gaming and everyday work"),
 "cpu-02":("AM4",6,12,3.5,4.4,32,65,"none","DDR4","Zen 3",2022,"4.0",True,1530,11000,60,"solid budget gaming on the AM4 platform"),
 "cpu-03":("AM4",6,12,3.7,4.6,32,65,"none","DDR4","Zen 3",2020,"4.0",False,1600,11200,63,"a slightly faster 5600 for budget AM4 gaming"),
 "cpu-04":("AM4",8,16,3.4,4.6,32,65,"none","DDR4","Zen 3",2022,"4.0",False,1550,14000,65,"more cores for streaming and editing on AM4"),
 "cpu-05":("AM5",6,12,4.2,4.7,16,65,"none","DDR5","Zen 4",2024,"4.0",True,1700,12500,66,"entry ticket to AM5 with an upgrade path"),
 "cpu-06":("AM5",6,12,3.8,5.1,32,65,"Radeon 610M","DDR5","Zen 4",2023,"5.0",True,1900,14800,77,"fast modern gaming CPU with an upgrade path"),
 "cpu-07":("AM5",8,16,4.2,5.0,96,120,"Radeon 610M","DDR5","Zen 4 3D V-Cache",2023,"5.0",False,1750,18000,100,"the fastest gaming CPU in our stock thanks to 96 MB 3D V-Cache"),
 "cpu-08":("AM5",8,16,3.8,5.5,32,65,"Radeon 610M","DDR5","Zen 5",2024,"5.0",False,2170,20500,87,"high-end gaming plus heavy multitasking"),
 "cpu-09":("LGA1200",4,8,3.7,4.4,6,65,"UHD 630","DDR4","Comet Lake",2021,"3.0",True,1150,5400,40,"basic office PCs without a graphics card"),
 "cpu-10":("LGA1200",6,12,2.9,4.3,12,65,"UHD 630","DDR4","Comet Lake",2020,"3.0",True,1150,8400,46,"office and light work on an older platform"),
 "cpu-11":("LGA1700",4,8,3.3,4.3,12,58,"none","DDR4/DDR5","Alder Lake",2022,"4.0",True,1700,8700,55,"cheap entry gaming; only 4 cores"),
 "cpu-12":("LGA1700",6,12,2.5,4.4,18,65,"none","DDR4/DDR5","Alder Lake",2022,"4.0",True,1720,12100,66,"great value 6-core for 1080p gaming"),
 "cpu-13":("LGA1700",10,16,2.5,4.6,20,65,"none","DDR4/DDR5","Raptor Lake",2023,"4.0",True,1800,16000,72,"6P+4E cores for gaming plus streaming"),
 "cpu-14":("LGA1700",14,20,3.5,5.3,24,125,"UHD 770","DDR4/DDR5","Raptor Lake Refresh",2023,"5.0",False,2050,24500,86,"unlocked high-end chip for gaming and creators"),
 "cpu-15":("LGA1700",20,28,3.4,5.6,33,125,"UHD 770","DDR4/DDR5","Raptor Lake Refresh",2023,"5.0",False,2140,35000,91,"workstation-class multi-core power"),
 "cpu-16":("AM5",8,16,3.8,5.3,32,65,"Radeon 610M","DDR5","Zen 4",2023,"5.0",True,1950,19500,81,"8 efficient cores for gaming and creative work"),
 "cpu-17":("AM5",6,12,3.7,5.0,32,65,"none","DDR5","Zen 4",2023,"5.0",True,1850,14500,76,"best value AM5 gaming CPU (a 7600 without graphics)"),
 "cpu-18":("LGA1700",10,16,2.5,4.7,20,65,"none","DDR4/DDR5","Raptor Lake Refresh",2024,"4.0",True,1830,17000,74,"strong all-rounder with 6P+4E cores"),
 "cpu-19":("AM5",6,12,3.9,5.4,32,65,"Radeon 610M","DDR5","Zen 5",2024,"5.0",False,2150,16800,85,"fastest 6-core for gaming in our stock"),
 "cpu-20":("AM5",6,12,4.7,5.3,32,105,"Radeon 610M","DDR5","Zen 4",2022,"5.0",False,1960,15200,80,"unlocked high-clock 6-core for gaming"),
}
# peak package power in W (Intel PL2 / AMD PPT): what the PSU and cooler really have to handle
PMAX = {"cpu-01":88,"cpu-02":88,"cpu-03":88,"cpu-04":88,"cpu-05":88,"cpu-06":88,"cpu-07":162,"cpu-08":88,
        "cpu-09":90,"cpu-10":134,"cpu-11":89,"cpu-12":117,"cpu-13":148,"cpu-14":181,"cpu-15":253,"cpu-16":88,
        "cpu-17":88,"cpu-18":148,"cpu-19":88,"cpu-20":142}
# RTX 50 (Blackwell) cards support DLSS 5 neural rendering (launched 3 Sep 2026); RTX 40 support announced, not dated
# id: chip, arch, vram, vramtype, bus, boost, tdp, len, psu, rt, upscaler, gaming, note
GPU = {
 "gpu-01":("GT 1030","Pascal",2,"GDDR5",64,1468,30,170,300,False,"none",8,"display output and very light games only"),
 "gpu-02":("GTX 1660 Ti","Turing",6,"GDDR6",192,1770,120,230,450,False,"FSR (game support)",30,"older 1080p medium-settings card"),
 "gpu-03":("Radeon RX 9050","RDNA 4",8,"GDDR6",128,2900,130,240,500,True,"FSR 4",48,"entry 1080p gaming"),
 "gpu-04":("GeForce RTX 5050","Blackwell",8,"GDDR6",128,2570,130,205,550,True,"DLSS 4 + DLSS 5",46,"entry 1080p gaming with DLSS 4"),
 "gpu-05":("Radeon RX 7600","RDNA 3",8,"GDDR6",128,2655,165,240,550,True,"FSR 3",45,"1080p high settings"),
 "gpu-06":("GeForce RTX 5060","Blackwell",8,"GDDR7",128,2497,145,230,550,True,"DLSS 4 + DLSS 5",55,"1080p ultra / high refresh"),
 "gpu-07":("GeForce RTX 5060","Blackwell",8,"GDDR7",128,2497,145,250,550,True,"DLSS 4 + DLSS 5",55,"1080p ultra / high refresh"),
 "gpu-08":("GeForce RTX 5060 Ti","Blackwell",8,"GDDR7",128,2572,180,300,600,True,"DLSS 4 + DLSS 5",64,"fast 1080p and entry 1440p"),
 "gpu-09":("Radeon RX 9060 XT","RDNA 4",16,"GDDR6",128,3130,182,290,600,True,"FSR 4",68,"1440p gaming with plenty of VRAM"),
 "gpu-10":("GeForce RTX 5060 Ti","Blackwell",8,"GDDR7",128,2572,180,250,600,True,"DLSS 4 + DLSS 5",64,"fast 1080p and entry 1440p"),
 "gpu-11":("Radeon RX 9070 GRE","RDNA 4",12,"GDDR6",192,2790,220,300,650,True,"FSR 4",80,"strong 1440p high-refresh gaming"),
 "gpu-12":("Radeon RX 9070","RDNA 4",16,"GDDR6",256,2520,220,330,700,True,"FSR 4",88,"1440p ultra and entry 4K"),
 "gpu-13":("GeForce RTX 5060 Ti","Blackwell",16,"GDDR7",128,2572,180,250,600,True,"DLSS 4 + DLSS 5",66,"1440p and AI/creator work that needs VRAM"),
}
MB = {  # chipset, m2, wifi, slots
 "mb-01":("A520",1,False,2),"mb-02":("B450",1,False,2),"mb-03":("B550",2,False,2),"mb-04":("B550",2,True,4),
 "mb-05":("B650",2,False,4),"mb-06":("B650",2,False,4),"mb-07":("B650",3,True,4),"mb-08":("H470",1,False,2),
 "mb-09":("H610",1,False,2),"mb-10":("B760",2,False,4),"mb-11":("B760",2,False,4),"mb-12":("B760",2,True,4),
 "mb-13":("Z790",4,True,4),"mb-14":("B850",2,True,4),"mb-15":("B850",4,True,4),
}
RAM = {"ram-01":(16,"1x8",False),"ram-02":(16,"2x8",False),"ram-03":(18,"2x16",False),"ram-04":(16,"2x8",True),
       "ram-05":(38,"1x16",False),"ram-06":(36,"2x16",False),"ram-07":(36,"2x16",True)}
SSD = {"ssd-01":("PCIe 4.0",3500,2100,160),"ssd-02":("PCIe 4.0",3500,2100,320),"ssd-03":("PCIe 4.0",7000,5000,600),
       "ssd-04":("PCIe 4.0",4150,4150,600),"ssd-05":("PCIe 4.0",5000,4200,440),"ssd-06":("SATA III",500,450,160),
       "ssd-07":("USB 3.2 Gen 2",1050,950,0),"hdd-01":("SATA III",190,190,0),"hdd-02":("SATA III",215,215,0)}
PSU = {"psu-01":("80+ Bronze","No",False),"psu-02":("80+ Bronze","No",False),"psu-03":("80+ Bronze","No",False),
       "psu-04":("80+ Gold","Full",False),"psu-05":("80+ Gold","Full",True),"psu-06":("80+ Gold","Full",True)}
CASE = {"case-01":1,"case-02":1,"case-03":3,"case-04":2,"case-05":2,"case-06":3}
COOL = {"cool-01":(1,False),"cool-02":(2,False),"cool-03":(1,False),"cool-04":(3,False),"cool-05":(2,True)}
MON = {"mon-01":("IPS",5,"FreeSync"),"mon-02":("Rapid IPS",1,"Adaptive-Sync"),"mon-03":("IPS",1,"G-Sync compatible"),
       "mon-04":("Nano IPS",1,"G-Sync compatible"),"mon-05":("IPS",1,"FreeSync Premium")}
PERIPH = {
 "kb-01":("type=Membrane;layout=Full-size;conn=USB wired","Spill-resistant full-size membrane keyboard for office use."),
 "kb-02":("type=Mechanical;layout=TKL 87-key;switch=Outemu;conn=USB wired","Tenkeyless mechanical gaming keyboard with red backlight."),
 "ms-01":("dpi=8000;sensor=Optical;rgb=true;conn=USB wired","Light 85 g gaming mouse with 8,000 DPI sensor and RGB."),
 "ms-02":("dpi=6400;sensor=Optical;conn=USB wired","Ergonomic right-handed esports mouse, 6,400 DPI."),
 "hs-01":("driver=50 mm;conn=3.5 mm;mic=Swivel-to-mute","Lightweight stereo gaming headset with swivel-to-mute mic."),
 "hs-02":("driver=50 mm;conn=USB 7.1 virtual;mic=Detachable;rgb=true","RGB USB headset with virtual 7.1 surround."),
 "hs-03":("driver=40 mm;conn=3.5 mm + USB light;mic=Flexible","Budget wired gaming headset."),
}

def kv(d): return ";".join(f"{k}={str(v).lower() if isinstance(v,bool) else v}" for k,v in d.items())
def fmt_gb(gb): return f"{gb//1000} TB" if gb >= 1000 and gb % 1000 == 0 else f"{gb} GB"

def enrich(r):
    pid, cat, brand, name = r[0], r[1], r[2], r[3]
    old = dict(x.split("=",1) for x in r[10].split(";") if "=" in x)
    if pid in CPU:
        so,co,th,ba,bo,l3,tdp,ig,ram,arch,yr,pcie,box,st,mt,gm,note = CPU[pid]
        perf = round(0.55*gm + 0.45*min(100, mt/350))
        specs = dict(socket=so,cores=co,threads=th,base=ba,boost=bo,l3=l3,tdp=tdp,pmax=PMAX[pid],igpu=ig!="none",igpuName=ig,
                     ram=ram,arch=arch,year=yr,pcie=pcie,boxcooler=box,st=st,mt=mt,gaming=gm)
        gfx = "has integrated " + ig + " graphics" if ig != "none" else "needs a graphics card"
        summ = (f"{co} cores / {th} threads, {ba}–{bo} GHz, {l3} MB L3 ({arch}, {so}, {ram}); {gfx}. "
                f"{note[0].upper() + note[1:]}.")
        return perf, summ, specs
    if pid in GPU:
        ch,ar,vr,vt,bus,bst,tdp,ln,psu,rt,up,gm,note = GPU[pid]
        specs = dict(chip=ch,arch=ar,vram=vr,vramType=vt,busBits=bus,boost=bst,tdp=tdp,len=ln,psu=psu,rt=rt,upscaler=up,gaming=gm)
        summ = f"{ch} with {vr} GB {vt} ({bus}-bit), {ar}, about {tdp} W, {ln} mm long. {note[0].upper() + note[1:]}."
        return gm, summ, specs
    if cat == "MOTHERBOARD":
        chs,m2,wifi,slots = MB[pid]
        specs = dict(socket=old["socket"],chipset=chs,ram=old["ram"],form=old["form"],m2=m2,ramSlots=slots,wifi=wifi)
        summ = (f"{chs} {old['form']} board for {old['socket']} CPUs, {old['ram']}, {slots} RAM slots, {m2}× M.2"
                + (", built-in Wi-Fi." if wifi else ", no Wi-Fi."))
        return 0, summ, specs
    if cat == "RAM":
        cl,kit,rgb = RAM[pid]
        specs = dict(type=old["type"],gb=int(old["gb"]),mhz=int(old["mhz"]),cl=cl,kit=kit,rgb=rgb)
        dual = "dual-channel kit" if kit.startswith("2x") else "single stick (add a second for dual-channel)"
        summ = f"{old['gb']} GB {old['type']}-{old['mhz']} CL{cl}, {kit} {dual}{', RGB' if rgb else ''}."
        return 0, summ, specs
    if cat == "STORAGE":
        gen,rd,wr,tbw = SSD[pid]
        specs = dict(iface=old["iface"],gb=int(old["gb"]),gen=gen,read=rd,write=wr)
        if tbw: specs["tbw"] = tbw
        kind = {"NVMe":"NVMe SSD","SATA":"2.5-inch SATA SSD","HDD":"7200 RPM hard drive","Portable":"portable SSD"}[old["iface"]]
        summ = f"{fmt_gb(int(old['gb']))} {kind}, up to {rd:,} MB/s read / {wr:,} MB/s write ({gen})."
        return 0, summ, specs
    if cat == "PSU":
        eff,mod,atx3 = PSU[pid]
        specs = dict(watt=int(old["watt"]),eff=eff,modular=mod,atx3=atx3)
        summ = f"{old['watt']} W {eff} power supply, {'fully modular' if mod=='Full' else 'non-modular'}{', ATX 3.1 with 12V-2x6' if atx3 else ''}."
        return 0, summ, specs
    if cat == "CASE":
        specs = dict(form=old["form"],maxgpu=int(old["maxgpu"]),maxcooler=int(old["maxcooler"]),fans=CASE[pid])
        summ = (f"{old['form']} case, fits GPUs up to {old['maxgpu']} mm and coolers up to {old['maxcooler']} mm, "
                f"{CASE[pid]} fan{'s' if CASE[pid]>1 else ''} included.")
        return 0, summ, specs
    if cat == "COOLER":
        fans,rgb = COOL[pid]
        specs = {**{k:(int(v) if v.isdigit() else v) for k,v in old.items()}, "fans":fans, "rgb":rgb}
        t = "240 mm liquid AIO" if old["type"]=="aio" else "tower air cooler"
        summ = f"{t} rated for about {old['tdp']} W, {fans} fan{'s' if fans>1 else ''}{', ARGB' if rgb else ''}."
        return 0, summ, specs
    if cat == "MONITOR":
        panel,ms,sync = MON[pid]
        specs = dict(inch=old["inch"],res=old["res"],hz=int(old["hz"]),panel=panel,ms=ms,sync=sync)
        summ = f"{old['inch']}-inch {old['res']} {panel} at {old['hz']} Hz, {ms} ms, {sync}."
        return 0, summ, specs
    if pid in PERIPH:
        s, summ = PERIPH[pid]
        return 0, summ, dict(x.split("=",1) for x in s.split(";"))
    return int(r[8] or 0), r[9], old

rows = list(csv.reader(io.StringIO(CSV.read_text(encoding="utf-8"))))
header, body = rows[0], rows[1:]
ids = {r[0] for r in body}
body += [r for r in NEW if r[0] not in ids]
out = io.StringIO()
w = csv.writer(out, lineterminator="\n")
w.writerow(header)
order = ["CPU","GPU","MOTHERBOARD","RAM","STORAGE","PSU","CASE","COOLER","MONITOR","KEYBOARD","MOUSE","HEADSET"]
body.sort(key=lambda r: (order.index(r[1]), r[0]))
for r in body:
    perf, summ, specs = enrich(r)
    r = [str(x) for x in r]
    r[8], r[9], r[10] = str(perf), summ, kv(specs)
    w.writerow(r)
CSV.write_text(out.getvalue(), encoding="utf-8")
print("wrote", len(body), "products to", CSV)
