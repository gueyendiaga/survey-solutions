import { escapeHtml, htmlToText } from './html-text';

describe('htmlToText', () => {
  it('retire les balises du Designer et décode les entités', () => {
    expect(htmlToText('1.04 Quel âge avait <font color="blue"> %rostertitle%</font> à son <b>dernier</b> anniversaire?'))
      .toBe('1.04 Quel âge avait %rostertitle% à son dernier anniversaire?');
    expect(htmlToText('Eau &amp; assainissement<br/>(ménage)')).toBe('Eau & assainissement (ménage)');
  });

  it('laisse le texte simple intact et gère les valeurs vides', () => {
    expect(htmlToText('Dakar')).toBe('Dakar');
    expect(htmlToText(null)).toBe('');
    expect(htmlToText(undefined)).toBe('');
  });

  it('n’exécute rien en lisant un libellé malveillant', () => {
    (window as unknown as { pwned?: boolean }).pwned = false;
    expect(htmlToText('<img src=x onerror="window.pwned = true">Oui<script>window.pwned = true</script>')).toBe('Oui');
    expect((window as unknown as { pwned?: boolean }).pwned).toBeFalse();
  });
});

describe('escapeHtml', () => {
  it('neutralise les caractères HTML', () => {
    expect(escapeHtml('<b>"A" & \'B\'</b>')).toBe('&lt;b&gt;&quot;A&quot; &amp; &#39;B&#39;&lt;/b&gt;');
  });
});
