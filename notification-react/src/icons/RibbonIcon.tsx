// antd'nin hazir ikon setinde bookmark/serit ikonu yok - kendi SVG'mizi
// antd'nin ozel ikon mekanizmasiyla saricyoruz, boylece diger antd
// ikonlariyla ayni davraniyor (font-size ile boyut, currentColor ile renk).

import AntdIcon from '@ant-design/icons';
import type { SVGProps } from 'react';

function RibbonOutlineSvg(props: SVGProps<SVGSVGElement>) {
  return (
    <svg {...props} viewBox="0 0 24 24" fill="currentColor">
      <path d="M17 3H7c-1.1 0-2 .9-2 2v16l7-3 7 3V5c0-1.1-.9-2-2-2zm0 15l-5-2.18L7 18V5h10v13z" />
    </svg>
  );
}

function RibbonFilledSvg(props: SVGProps<SVGSVGElement>) {
  return (
    <svg {...props} viewBox="0 0 24 24" fill="currentColor">
      <path d="M17 3H7c-1.1 0-2 .9-2 2v16l7-3 7 3V5c0-1.1-.9-2-2-2z" />
    </svg>
  );
}

export function RibbonOutlined(props: Record<string, unknown>) {
  return <AntdIcon component={RibbonOutlineSvg} {...props} />;
}

export function RibbonFilled(props: Record<string, unknown>) {
  return <AntdIcon component={RibbonFilledSvg} {...props} />;
}
